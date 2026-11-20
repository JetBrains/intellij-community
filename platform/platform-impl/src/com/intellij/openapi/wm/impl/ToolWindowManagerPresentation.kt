// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.isClosingWithFrame
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.toolWindow.ToolWindowEventSource
import com.intellij.toolWindow.ToolWindowProjectSelector
import com.intellij.util.concurrency.ThreadingAssertions
import java.awt.Component

private val LOG = logger<ToolWindowManagerPresentation>()

/**
 * Presents the tool windows of the frame guests in the tool windows of the frame owner. [manager] is the manager of the frame owner.
 *
 * For a tool window id, the tool window of the frame owner is the presenting one. It keeps the stripe button, the place and the
 * visible state. Each frame guest that registers the id has a presented tool window, see [ToolWindowImpl.isPresented].
 * The presenting tool window shows the tool window of the project that its [ToolWindowProjectSelection] selects.
 * When the frame owner has no tool window with the id, this class registers one that has no content of its own.
 *
 * The selector lists a frame guest while its tool window is available and on the stripe of its project, see
 * [ToolWindowImpl.isOnProjectStripe]. So a project whose Find tool window lost its last content has no entry.
 *
 * This class is the only writer of the visible state and the place of a presented tool window, see
 * [ToolWindowManagerImpl.applyPresented]. It does no work on a tool window pane. Every function runs on the EDT.
 */
internal class ToolWindowManagerPresentation(private val manager: ToolWindowManagerImpl) {
  private val slots = HashMap<String, Slot>()

  /** The projects that the selector of the tool window [id] lists. It is empty for a tool window that presents no frame guest. */
  fun entriesOf(id: String): List<Project> = slots.get(id)?.selection?.entries ?: emptyList()

  /**
   * The tool window [id] that this class registered for the frame guests, or `null`.
   * The code of the frame owner that registers [id] after a frame guest gets this tool window.
   */
  fun registeredForGuests(id: String): ToolWindowImpl? = slots.get(id)?.takeIf { it.isRegisteredForGuests }?.presenting

  /** The project whose tool window the tool window [id] shows, or `null` when no project is selected. */
  fun selectedProjectOf(id: String): Project? = slots.get(id)?.selection?.selected

  /**
   * The tool window of a frame guest that the tool window [id] shows now.
   * `null` when it shows its own content, when it is hidden, and when no project is selected.
   */
  fun shownToolWindowOf(id: String): ToolWindowImpl? {
    val slot = slots.get(id) ?: return null
    if (!slot.presenting.isVisible) {
      return null
    }
    return slot.toolWindows.get(slot.selection.selected ?: return null)?.takeIf { it !== slot.presenting }
  }

  /**
   * The decorators of the tool windows of the frame guests that no tool window of the pane [paneId] shows now.
   * A change of the look and feel must reach them, because they are in no component hierarchy.
   */
  fun hiddenPresentedDecorators(paneId: String): Sequence<Component> {
    return slots.values.asSequence()
      .filter { manager.getToolWindowPane(it.presenting).paneId == paneId }
      .flatMap { slot -> slot.toolWindows.values.asSequence().filter { it !== slot.presenting } }
      .mapNotNull { toolWindow -> toolWindow.decoratorComponent?.takeIf { it.parent == null } }
  }

  /**
   * Adds the presented tool window [toolWindow] of a frame guest to the tool window of the frame owner with the same id.
   * [task] is the registration of [toolWindow]. A tool window without content of its own takes its look from it.
   */
  fun attach(toolWindow: ToolWindowImpl, task: RegisterToolWindowTaskData) {
    ThreadingAssertions.assertEventDispatchThread()
    check(toolWindow.isPresented) { "The tool window ${toolWindow.id} is not presented" }

    val slot = slots.get(toolWindow.id) ?: createSlot(task) ?: return
    slot.toolWindows.put(toolWindow.project, toolWindow)
    syncEntry(slot, toolWindow.project)
  }

  /**
   * Removes the presented tool window [toolWindow] of a frame guest: the guest unregisters it, or the guest closes.
   * When the frame goes away with the guest, the presenting tool window keeps its state, so the next start restores it.
   */
  fun detach(toolWindow: ToolWindow) {
    ThreadingAssertions.assertEventDispatchThread()
    val slot = slotOf(toolWindow) ?: return
    val guest = toolWindow.project
    slot.toolWindows.remove(guest)
    slot.selection.remove(guest)
    if (guest.isClosingWithFrame) {
      // the decorator of the guest must leave the frame before the guest disposes it
      updateUi(slot)
    }
    else {
      refresh(slot)
    }
  }

  /** The tool window [toolWindow] of the frame owner has its decorator now, so it can show the tool window of a frame guest. */
  fun presentingDecoratorCreated(toolWindow: ToolWindowImpl) {
    updateUi(slots.get(toolWindow.id)?.takeIf { it.presenting === toolWindow } ?: return)
  }

  /** Forgets the tool window [id] of the frame owner, which the owner unregisters. */
  fun presentingUnregistered(id: String) {
    slots.remove(id)
  }

  /**
   * A tool window of a frame guest, or the tool window of the frame owner, changed its `isAvailable` state.
   * Or a tool window of a frame guest came to the stripe of its project or left it.
   */
  fun availabilityChanged(toolWindow: ToolWindowImpl) {
    ThreadingAssertions.assertEventDispatchThread()
    syncEntry(slotOf(toolWindow) ?: return, toolWindow.project)
  }

  /** The tool window [toolWindow] of the frame owner got a new window info: its visible state or its place can differ now. */
  fun presentingInfoApplied(toolWindow: ToolWindowImpl) {
    val slot = slots.get(toolWindow.id)?.takeIf { it.presenting === toolWindow } ?: return
    if (toolWindow.isVisible) {
      slot.selection.resolve()
    }
    applySelection(slot)
  }

  /** The user selects [project] in the selector of the tool window [id]. */
  fun select(id: String, project: Project) {
    ThreadingAssertions.assertEventDispatchThread()
    val slot = slots.get(id) ?: return
    if (project in slot.selection.entries && slot.selection.select(project)) {
      applySelection(slot)
    }
  }

  /** The code of a frame guest asks to show its presented tool window [toolWindow]. The request selects the guest. */
  fun show(toolWindow: ToolWindowImpl) {
    ThreadingAssertions.assertEventDispatchThread()
    val slot = selectFor(toolWindow) ?: return
    if (slot.presenting.isVisible) {
      applySelection(slot)
    }
    else {
      manager.showToolWindow(slot.presenting.id)
    }
  }

  /**
   * The code of a frame guest asks to activate its presented tool window [toolWindow]. The request selects the guest.
   * The tool window gets the focus on a request of the user, which has a [source], or when the guest is the active project of
   * the frame. Another request must not take the focus from the project where the user works.
   */
  fun activate(toolWindow: ToolWindowImpl, autoFocusContents: Boolean, source: ToolWindowEventSource?) {
    ThreadingAssertions.assertEventDispatchThread()
    val slot = selectFor(toolWindow) ?: return
    if (slot.presenting.isVisible) {
      applySelection(slot)
    }
    val isFocusAllowed = source != null || IdeFrameGuests.activeProjectOf(manager.project) === toolWindow.project
    manager.activateToolWindow(
      id = slot.presenting.id,
      runnable = null,
      autoFocusContents = autoFocusContents && isFocusAllowed,
      source = source,
    )
  }

  /**
   * The code of a frame guest asks to hide its presented tool window [toolWindow].
   * The presenting tool window hides when it shows [toolWindow]. It leaves the stripe only on the request of the user.
   */
  fun hide(toolWindow: ToolWindowImpl, hideSide: Boolean, moveFocus: Boolean, removeFromStripe: Boolean, source: ToolWindowEventSource?) {
    ThreadingAssertions.assertEventDispatchThread()
    val slot = slotOf(toolWindow) ?: return
    if (slot.selection.selected !== toolWindow.project || !slot.presenting.isVisible) {
      return
    }
    manager.hideToolWindow(
      id = slot.presenting.id,
      hideSide = hideSide,
      moveFocus = moveFocus,
      removeFromStripe = removeFromStripe && source == ToolWindowEventSource.RemoveStripeButtonAction,
      source = source,
    )
  }

  private fun slotOf(toolWindow: ToolWindow): Slot? {
    return slots.get(toolWindow.id)?.takeIf { it.toolWindows.get(toolWindow.project) === toolWindow }
  }

  private fun selectFor(toolWindow: ToolWindowImpl): Slot? {
    val slot = slotOf(toolWindow) ?: return null
    // a tool window that is not available has no entry
    if (toolWindow.project !in slot.selection.entries) {
      return null
    }
    slot.selection.select(toolWindow.project)
    return slot
  }

  private fun createSlot(task: RegisterToolWindowTaskData): Slot? {
    val activeProject = { IdeFrameGuests.activeProjectOf(manager.project) }
    val own = manager.getEntry(task.id)?.toolWindow
    val slot = when {
      own == null -> {
        val presenting = manager.registerPresentingToolWindow(task).toolWindow
        Slot(presenting = presenting, showsOwnerContent = false, isRegisteredForGuests = true, activeProject)
      }
      own.isMultiProject -> {
        LOG.warn("The tool window ${task.id} of ${manager.project.name} presents every project itself, " +
                 "so it cannot present the tool window of a frame guest")
        return null
      }
      // the frame owner registered the tool window from code, but the frame must not show the content of the owner
      service<ProjectFrameCapabilitiesService>().has(manager.project, ProjectFrameCapability.SUPPRESS_OWN_TOOL_WINDOW_CONTENT) -> {
        Slot(presenting = own, showsOwnerContent = false, isRegisteredForGuests = false, activeProject)
      }
      else -> {
        val slot = Slot(presenting = own, showsOwnerContent = true, isRegisteredForGuests = false, activeProject)
        slot.toolWindows.put(manager.project, own)
        if (own.isAvailable) {
          slot.selection.add(manager.project, first = true)
          if (own.isVisible) {
            // the tool window shows the content of the frame owner now, and the user sees it
            slot.selection.select(manager.project)
          }
        }
        slot
      }
    }
    slots.put(task.id, slot)
    return slot
  }

  /**
   * Makes [project] an entry of the selection when its tool window is available, and no entry otherwise.
   * A tool window of a frame guest must also be on the stripe of its project.
   */
  private fun syncEntry(slot: Slot, project: Project) {
    val toolWindow = slot.toolWindows.get(project)
    if (toolWindow != null && toolWindow.isAvailable && (!toolWindow.isPresented || toolWindow.isOnProjectStripe)) {
      slot.selection.add(project, first = project === manager.project)
    }
    else {
      slot.selection.remove(project)
    }
    refresh(slot)
  }

  private fun refresh(slot: Slot) {
    if (!slot.showsOwnerContent) {
      updateAvailability(slot)
    }
    if (slot.presenting.isVisible) {
      slot.selection.resolve()
    }
    applySelection(slot)
  }

  /**
   * A tool window that shows no content of the frame owner is available while a project has an entry.
   * It comes back when a project gets an entry again, if the removal of the last entry hid it.
   */
  private fun updateAvailability(slot: Slot) {
    val presenting = slot.presenting
    val isAvailable = slot.selection.entries.isNotEmpty()
    if (presenting.isAvailable == isAvailable) {
      return
    }

    if (isAvailable) {
      presenting.setAvailable(true)
      if (slot.isHiddenForNoEntry) {
        slot.isHiddenForNoEntry = false
        if (!presenting.isVisible) {
          manager.showToolWindow(presenting.id)
        }
      }
    }
    else {
      slot.isHiddenForNoEntry = presenting.isVisible
      presenting.setAvailable(false)
    }
  }

  /** Gives each presented tool window its state: the selected one is visible while the presenting tool window is visible. */
  private fun applySelection(slot: Slot) {
    val presenting = slot.presenting
    val shown = if (presenting.isVisible) slot.selection.selected else null
    // a hide comes before a show, so a listener never sees two visible tool windows
    for ((project, toolWindow) in slot.toolWindows) {
      if (toolWindow !== presenting && project !== shown) {
        toolWindow.toolWindowManager.applyPresented(id = toolWindow.id, visible = false, place = presenting.windowInfo)
      }
    }
    val shownToolWindow = shown?.let(slot.toolWindows::get)
    if (shownToolWindow != null && shownToolWindow !== presenting) {
      shownToolWindow.toolWindowManager.applyPresented(id = shownToolWindow.id, visible = true, place = presenting.windowInfo)
    }
    updateUi(slot)
  }

  /**
   * Puts the selector and the decorator of the shown tool window of a frame guest into the decorator of the presenting tool window.
   * The selector shows only with a choice: a tool window with one project looks as in a frame with one project.
   * A tool window that was never shown has no decorator, and [presentingDecoratorCreated] calls this function when it gets one.
   */
  private fun updateUi(slot: Slot) {
    val decorator = slot.presenting.decorator ?: return
    val entries = slot.selection.entries
    if (entries.size > 1) {
      val selector = slot.selector ?: ToolWindowProjectSelector(manager.project, slot.presenting.disposable) { project ->
        select(slot.presenting.id, project)
      }.also { slot.selector = it }
      selector.update(entries, slot.selection.selected)
      decorator.setProjectSelector(selector.component)
    }
    else {
      decorator.setProjectSelector(null)
      // a hidden selector must not hold the project of a frame guest that closes
      slot.selector?.update(emptyList(), null)
    }
    decorator.present(shownToolWindowOf(slot.presenting.id)?.getOrCreateDecoratorComponent())
  }

  private class Slot(
    /** The tool window of the frame owner. */
    @JvmField val presenting: ToolWindowImpl,
    /**
     * [presenting] shows the content of the frame owner, so the frame owner can be an entry.
     * Otherwise, [presenting] only presents the frame guests, and their entries decide its available state.
     */
    @JvmField val showsOwnerContent: Boolean,
    /** This class registered [presenting], because the frame owner had no tool window with the id. */
    @JvmField val isRegisteredForGuests: Boolean,
    activeProject: () -> Project?,
  ) {
    @JvmField
    val selection = ToolWindowProjectSelection(activeProject)

    /** The tool window of each project of the frame that registered the id: [presenting] for a frame owner that shows its content. */
    @JvmField
    val toolWindows = LinkedHashMap<Project, ToolWindowImpl>()

    /** The removal of the last entry hid [presenting]. */
    @JvmField
    var isHiddenForNoEntry = false

    /** The selector of the project. It exists from the first time [presenting] has a choice of projects. */
    @JvmField
    var selector: ToolWindowProjectSelector? = null
  }
}

/** The factory of a tool window of the frame owner that has no content of its own and only presents the tool windows of the frame guests. */
internal object PresentingToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
  }
}
