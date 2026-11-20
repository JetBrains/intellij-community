// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.MultiProjectToolWindowFactory
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.toolWindow.ToolWindowEntry
import com.intellij.toolWindow.ToolWindowEventSource
import com.intellij.toolWindow.ToolWindowFactoryScope
import com.intellij.toolWindow.ToolWindowProjectSelector
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.EDT
import java.awt.Component
import java.util.function.Supplier

private val LOG = logger<ToolWindowManagerPresentation>()

/**
 * Presents the tool windows of the frame guests in the tool windows of the frame owner. [manager] is the manager of the frame owner.
 *
 * For a tool window id, the tool window of the frame owner is the presenting one. It keeps the stripe button, the place and the
 * visible state. Each frame guest that registers the id has a presented tool window, see [ToolWindowImpl.isPresented].
 * The presenting tool window shows the tool window of the project that its [ToolWindowProjectSelection] selects.
 * When the frame owner has no tool window with the id, this class registers one that has no content of its own.
 *
 * The selector lists a project while its tool window is available and on the stripe of its project, see
 * [ToolWindowImpl.isOnProjectStripe]. So a project whose Find tool window lost its last content has no entry.
 * The entries are in the order of the projects of the frame, see [IdeFrameGuests.orderedProjectsOf].
 * The rule is the same for a frame guest and for the frame owner. A frame owner without tool window content of its own
 * is never an entry.
 *
 * The presenting tool window is available while the frame owner makes it available or a frame guest has an entry, see
 * [ToolWindowImpl.hasFrameGuestEntry]. It keeps its stripe button while a project has an entry.
 *
 * This class is the only writer of the visible state and the place of a presented tool window, see
 * [ToolWindowManagerImpl.applyPresented]. It does no work on a tool window pane. Every function runs on the EDT.
 *
 * The class holds a presented tool window only while its frame guest has it: the disposal of the tool window removes it,
 * see [attach]. It holds no project of a frame guest.
 */
internal class ToolWindowManagerPresentation(private val manager: ToolWindowManagerImpl) {
  private val slots = HashMap<String, PresentingSlot>()

  /** The manager of the frame owner is disposed, so this class presents nothing from now on. */
  @Volatile
  private var isDisposed = false

  /** The frame type of the frame owner. A frame guest is in the frame of its owner, so this frame type decides its layout profile. */
  val projectFrameTypeId: String?
    get() = manager.projectFrameTypeId

  /** Waits for the tool windows of the frame owner that the extension point declares. */
  suspend fun awaitToolWindowsRegistered() {
    manager.awaitToolWindowsRegistered()
  }

  /** The projects that the selector of the tool window [id] lists. It is empty for a tool window that presents no frame guest. */
  fun entriesOf(id: String): List<Project> = slots.get(id)?.selection?.entries?.map { it.project } ?: emptyList()

  /**
   * The project of this frame that shows the empty state of a tool window on a request of the user. The tool window has no
   * content, so the request is for the active project of the frame. A frame guest whose tool windows this class presents
   * shows the empty state itself. The frame owner shows it otherwise. `null` when the frame owner has no tool window
   * content of its own: no project shows an empty state then.
   */
  fun emptyStateProject(): Project? {
    val frameOwner = manager.project
    val active = IdeFrameGuests.activeProjectOf(frameOwner)
    if (active !== frameOwner && (ToolWindowManager.getInstance(active) as? ToolWindowManagerImpl)?.frameOwnerPresentation === this) {
      return active
    }
    return if (ToolWindowFactoryScope.isLegacyToolWindowModeEnabled && manager.suppressesOwnContent) null else frameOwner
  }

  /** The project whose tool window the tool window [id] shows, or `null` when no project is selected. */
  fun selectedProjectOf(id: String): Project? = slots.get(id)?.selection?.selected?.project

  /**
   * The tool window of a frame guest that the tool window [id] shows now.
   * `null` when it shows its own content, when it is hidden, and when no project is selected.
   */
  fun shownToolWindowOf(id: String): ToolWindowImpl? {
    val slot = slots.get(id) ?: return null
    if (!slot.presenting.isVisible) {
      return null
    }
    return slot.selection.selected?.takeIf { it !== slot.presenting }
  }

  /**
   * The decorators of the tool windows of the frame guests that no tool window of the pane [paneId] shows now.
   * A change of the look and feel must reach them, because they are in no component hierarchy.
   */
  fun hiddenPresentedDecorators(paneId: String): Sequence<Component> {
    return slots.values.asSequence()
      .filter { manager.getToolWindowPane(it.presenting).paneId == paneId }
      .flatMap { slot -> slot.guests.asSequence() }
      .mapNotNull { toolWindow -> toolWindow.decoratorComponent?.takeIf { it.parent == null } }
  }

  /**
   * Adds the presented tool window [toolWindow] of a frame guest to the tool window of the frame owner with the same id.
   * [task] is the registration of [toolWindow]. A tool window without content of its own takes its look from it.
   *
   * The disposal of [toolWindow] removes it again, so this class never holds the tool window of a closed frame guest.
   * The call does nothing for a disposed tool window and for a disposed frame owner.
   */
  fun attach(toolWindow: ToolWindowImpl, task: RegisterToolWindowTaskData) {
    ThreadingAssertions.assertEventDispatchThread()
    check(toolWindow.isPresented) { "The tool window ${toolWindow.id} is not presented" }
    if (isDisposed || manager.project.isDisposed || toolWindow.presentingSlot != null) {
      return
    }
    if (!Disposer.tryRegister(toolWindow.disposable) { runOnEdt { detach(toolWindow) } }) {
      return
    }

    val slot = slots.get(toolWindow.id) ?: createSlot(task) ?: return
    slot.guests.add(toolWindow)
    toolWindow.presentingSlot = slot
    if (slot.isPlaceholder && slot.lastGuestStripeTitle == null) {
      slot.lastGuestStripeTitle = toolWindow.stripeTitle
    }
    syncEntry(slot, toolWindow)
  }

  /**
   * Removes the presented tool window [toolWindow] of a frame guest: the guest unregisters it, or the guest closes.
   * The tool window of the frame owner presents no frame guest after its last frame guest left, see [lastGuestLeft].
   *
   * The decorator of [toolWindow] leaves the frame now, before the guest disposes it. When [toolWindow] was the shown one,
   * the tool window of the frame owner selects another project in a later event, see [resolveLater].
   */
  fun detach(toolWindow: ToolWindowImpl) {
    ThreadingAssertions.assertEventDispatchThread()
    val slot = toolWindow.presentingSlot ?: return
    toolWindow.presentingSlot = null
    if (!slot.guests.remove(toolWindow) || isDisposed || slots.get(toolWindow.id) !== slot) {
      return
    }
    slot.selection.remove(toolWindow)
    if (slot.guests.isEmpty()) {
      lastGuestLeft(slot)
      return
    }

    refresh(slot, resolves = false)
    if (slot.presenting.isVisible && slot.selection.selected == null) {
      resolveLater(slot)
    }
  }

  /**
   * Selects a project for the visible tool window of [slot] in a later event, when it has none then.
   *
   * The selection of a project shows its tool window, which creates the content of the tool window. The close of a frame
   * and an exit close each project of the frame in one event. So the wait keeps a project that closes next from the creation
   * of content that nobody sees. The event waits for the end of a modal state, because the save of a project that closes
   * runs under a modal progress.
   */
  private fun resolveLater(slot: PresentingSlot) {
    if (slot.isResolveScheduled) {
      return
    }
    slot.isResolveScheduled = true
    ApplicationManager.getApplication().invokeLater({
      slot.isResolveScheduled = false
      if (!isDisposed && slots.get(slot.presenting.id) === slot) {
        refresh(slot)
      }
    }, ModalityState.nonModal(), manager.project.disposed)
  }

  /**
   * The last frame guest left [slot], so this class forgets the slot and everything that came from a frame guest.
   * A tool window that this class registered for the frame guests leaves with them, unless it has content of the frame owner.
   * The layout of the frame owner keeps its place and its visible state, so it shows again with the next frame guest
   * and after a restart. Another tool window is a tool window of the frame owner alone from now on.
   */
  private fun lastGuestLeft(slot: PresentingSlot) {
    val presenting = slot.presenting
    if (slot.isPlaceholder && presenting.contentManagerIfCreated?.isEmpty != false) {
      unbind(slot)
      manager.doUnregisterToolWindow(presenting.id)
      return
    }

    if (slot.isPlaceholder && slot.placeholderTask.stripeTitle != null) {
      // the title came from a frame guest, see `placeholderTaskOf`, and it stays as the last frame guest gave it
      @Suppress("HardCodedStringLiteral")
      val title = slot.lastGuestStripeTitle ?: presenting.id
      presenting.setStripeTitleProvider { title }
    }
    refresh(slot)
    slots.remove(presenting.id)
  }

  /** The tool window [toolWindow] of the frame owner has its decorator now, so it can show the tool window of a frame guest. */
  fun presentingDecoratorCreated(toolWindow: ToolWindowImpl) {
    updateUi(slots.get(toolWindow.id)?.takeIf { it.presenting === toolWindow } ?: return)
  }

  /**
   * The code of a frame guest registers [task]. A tool window of the whole frame is one for the frame, and the frame owner
   * has it: the factory of [task] has the marker [MultiProjectToolWindowFactory], or the frame owner has a tool window with
   * the id whose factory has it. The frame owner registers [task] when it has no tool window with the id.
   *
   * @return the tool window of the frame owner, or `null` when [task] is for the frame guest alone
   */
  fun registerForFrame(task: RegisterToolWindowTaskData): ToolWindow? {
    ThreadingAssertions.assertEventDispatchThread()
    if (isDisposed || manager.project.isDisposed) {
      return null
    }

    val own = manager.getEntry(task.id)?.toolWindow
    if (task.contentFactory is MultiProjectToolWindowFactory) {
      return own ?: manager.registerForFrameGuest(task).toolWindow
    }
    return own?.takeIf { it.isMultiProject }
  }

  /**
   * The tool window [id] that the frame owner gets when it registers [id], in place of a registration. It is the tool window
   * that this class registered for the frame guests, when the frame owner put content into it already: a new tool window
   * would lose that content. `null` when the frame owner registers a tool window of its own.
   */
  fun placeholderWithOwnerContent(id: String): ToolWindowEntry? {
    val slot = slots.get(id)?.takeIf { it.isPlaceholder } ?: return null
    return if (slot.presenting.contentManagerIfCreated?.isEmpty == false) manager.getEntry(id) else null
  }

  /**
   * The frame owner registers the tool window [id] with [register]. When a frame guest registered [id] first, this class
   * registered a tool window without content of its own for it. The tool window of the frame owner takes its place and presents
   * the frame guests from now on.
   */
  fun registerForOwner(id: String, register: () -> ToolWindowEntry): ToolWindowEntry {
    val slot = slots.get(id)?.takeIf { it.isPlaceholder } ?: return register()
    ThreadingAssertions.assertEventDispatchThread()
    placeholderWithOwnerContent(id)?.let { return it }

    unbind(slot)
    manager.doUnregisterToolWindow(id)
    val entry = try {
      register()
    }
    catch (e: Throwable) {
      // the frame guests keep a tool window that presents them
      bind(slot, manager.registerForFrameGuest(slot.placeholderTask).toolWindow, isPlaceholder = true)
      throw e
    }
    bind(slot, entry.toolWindow, isPlaceholder = false)
    return entry
  }

  /**
   * The frame owner unregisters its tool window [toolWindow]. It stops to present the frame guests now, before it leaves the frame.
   *
   * @return the slot of the frame guests that [toolWindow] presented, for [presentingUnregistered], or `null`
   */
  fun presentingUnregistering(toolWindow: ToolWindowImpl): PresentingSlot? {
    val slot = slots.get(toolWindow.id)?.takeIf { it.presenting === toolWindow } ?: return null
    unbind(slot)
    return slot
  }

  /**
   * The frame owner unregistered the tool window of [slot]. The frame guests that still have the id get a new tool window
   * without content of its own, so their tool windows stay reachable.
   */
  fun presentingUnregistered(slot: PresentingSlot) {
    if (slot.guests.isEmpty() || isDisposed || manager.project.isDisposed) {
      slot.release()
      return
    }
    bind(slot, manager.registerForFrameGuest(slot.placeholderTask).toolWindow, isPlaceholder = true)
  }

  /**
   * Takes [slot] off its presenting tool window, which leaves: the frame guests are hidden, and the decorator of the tool window
   * holds no decorator of a frame guest. The slot keeps the frame guests for [bind].
   */
  private fun unbind(slot: PresentingSlot) {
    val presenting = slot.presenting
    slot.wasVisibleBeforeUnbind = presenting.isVisible
    slot.selection.remove(presenting)
    for (toolWindow in slot.guests.toList()) {
      toolWindow.toolWindowManager.applyPresented(id = toolWindow.id, visible = false, place = presenting.windowInfo)
    }
    presenting.decorator?.let {
      it.setProjectSelector(null)
      it.present(null)
    }
    // the selector lives as long as the tool window that leaves
    slot.selector?.update(emptyList(), null)
    slot.selector = null
    slots.remove(presenting.id)
  }

  /** Makes [toolWindow] the presenting tool window of [slot]. It shows when the tool window before it was visible. */
  private fun bind(slot: PresentingSlot, toolWindow: ToolWindowImpl, isPlaceholder: Boolean) {
    slot.presenting = toolWindow
    slot.isPlaceholder = isPlaceholder
    slots.put(toolWindow.id, slot)
    initOwnerEntry(slot)
    refresh(slot)
    if (slot.wasVisibleBeforeUnbind && !toolWindow.isVisible && toolWindow.isAvailable) {
      manager.showToolWindow(toolWindow.id)
    }
    slot.wasVisibleBeforeUnbind = false
  }

  /** Gives the frame owner its entry in a slot that got its presenting tool window, when the frame owner has one, see [isEntry]. */
  private fun initOwnerEntry(slot: PresentingSlot) {
    val presenting = slot.presenting
    // a tool window that hides without content left the stripe with its last content
    presenting.isOnProjectStripe = presenting.isShowStripeButton || !presenting.isToHideOnEmptyContent()
    if (isEntry(slot, presenting)) {
      slot.selection.add(presenting)
      sortEntries(slot)
      if (presenting.isVisible) {
        // the tool window shows the content of the frame owner now, and the user sees it
        slot.selection.select(presenting)
      }
    }
  }

  /** The manager of the frame owner is disposed: this class drops every tool window of a frame guest. */
  fun dispose() {
    isDisposed = true
    runOnEdt {
      for (slot in slots.values) {
        slot.release()
      }
      slots.clear()
    }
  }

  /**
   * A tool window of a frame guest changed its `isAvailable` state, or the frame owner changed the available state of its tool
   * window, see [ToolWindowImpl.isOwnAvailable]. Or a tool window of a frame guest came to the stripe of its project or left it.
   */
  fun availabilityChanged(toolWindow: ToolWindowImpl) {
    if (slots.isEmpty()) {
      return
    }
    ThreadingAssertions.assertEventDispatchThread()
    syncEntry(slotOf(toolWindow) ?: return, toolWindow)
  }

  /**
   * The code of the frame owner takes its tool window [toolWindow] off the stripe, as after the removal of its last content.
   * The frame owner then has no entry. The stripe button is also for the frame guests, so it stays while a frame guest has
   * an entry, and the tool window hides only when it shows the content of the frame owner.
   *
   * @return `true` when the tool window keeps its stripe button: the caller must not remove it
   */
  fun ownerLeavesStripe(toolWindow: ToolWindowImpl): Boolean {
    val slot = slots.get(toolWindow.id)?.takeIf { it.presenting === toolWindow } ?: return false
    val wasShown = toolWindow.isVisible && slot.selection.selected.let { it == null || it === toolWindow }
    toolWindow.isOnProjectStripe = false
    slot.selection.remove(toolWindow)
    if (!slot.hasGuestEntry()) {
      return false
    }

    if (wasShown) {
      // as in a frame with one project: the tool window hides with the last content of the project that it shows
      manager.hideToolWindow(toolWindow.id)
    }
    else {
      refresh(slot)
    }
    return true
  }

  /** The tool window [toolWindow] of the frame owner comes back to the stripe, as with new content. The frame owner can be an entry again. */
  fun ownerComesToStripe(toolWindow: ToolWindowImpl) {
    val slot = slots.get(toolWindow.id)?.takeIf { it.presenting === toolWindow } ?: return
    if (!toolWindow.isOnProjectStripe) {
      toolWindow.isOnProjectStripe = true
      syncEntry(slot, toolWindow)
    }
  }

  /**
   * The code of the frame owner asks to show or to activate its tool window [toolWindow]. The request selects the frame owner
   * when it has an entry, as a request of a frame guest selects the guest, see [show].
   */
  fun ownerShowRequested(toolWindow: ToolWindowImpl) {
    val slot = slots.get(toolWindow.id)?.takeIf { it.presenting === toolWindow } ?: return
    ThreadingAssertions.assertEventDispatchThread()
    if (toolWindow.isOwnAvailable) {
      // a show request brings the stripe button back
      ownerComesToStripe(toolWindow)
    }
    if (toolWindow in slot.selection.entries && slot.selection.select(toolWindow) && toolWindow.isVisible) {
      applySelection(slot)
    }
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
    select(slot, slot.selection.entries.firstOrNull { it.project === project } ?: return)
  }

  private fun select(slot: PresentingSlot, toolWindow: ToolWindowImpl) {
    if (toolWindow in slot.selection.entries && slot.selection.select(toolWindow)) {
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
    if (slot.selection.selected !== toolWindow || !slot.presenting.isVisible) {
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

  /** The slot that has [toolWindow]: as the presented tool window of a frame guest, or as the presenting tool window of the frame owner. */
  private fun slotOf(toolWindow: ToolWindowImpl): PresentingSlot? {
    return toolWindow.presentingSlot ?: slots.get(toolWindow.id)?.takeIf { it.presenting === toolWindow }
  }

  private fun selectFor(toolWindow: ToolWindowImpl): PresentingSlot? {
    val slot = slotOf(toolWindow) ?: return null
    // a tool window that is not available has no entry
    if (toolWindow !in slot.selection.entries) {
      return null
    }
    slot.selection.select(toolWindow)
    return slot
  }

  private fun createSlot(task: RegisterToolWindowTaskData): PresentingSlot? {
    val own = manager.getEntry(task.id)?.toolWindow
    if (own != null && own.isMultiProject) {
      LOG.warn("The tool window ${task.id} of ${manager.project.name} presents every project itself, " +
               "so it cannot present the tool window of a frame guest")
      return null
    }

    val placeholderTask = placeholderTaskOf(task)
    val slot = PresentingSlot(
      presenting = own ?: manager.registerForFrameGuest(placeholderTask).toolWindow,
      isPlaceholder = own == null,
      placeholderTask = placeholderTask,
    )
    slots.put(task.id, slot)
    initOwnerEntry(slot)
    return slot
  }

  /**
   * The registration of a tool window that has no content of its own and presents the tool windows of the frame guests with
   * the id of [guestTask]. The tool window takes its look and its default place from [guestTask]. It has a factory only when
   * [guestTask] has one: a tool window without a factory gets a stripe button by default. The tool window is not available
   * at first. It shows with the stored visible state when a frame guest gets an entry.
   */
  private fun placeholderTaskOf(guestTask: RegisterToolWindowTaskData): RegisterToolWindowTaskData {
    return guestTask.copy(
      component = null,
      // the dumb mode of the frame owner must not hide the tool window of a frame guest
      canWorkInDumbMode = true,
      contentFactory = if (guestTask.contentFactory == null) null else PresentingToolWindowFactory,
      // only a frame guest or content of the frame owner makes the tool window available, see `ToolWindowImpl.isAvailable`
      shouldBeAvailable = false,
      // as Find and Run: the tool window is on the stripe for the frame owner only while it has content of the frame owner
      hideOnEmptyContent = true,
      // The supplier of a frame guest can hold the guest, and this tool window can live longer than the guest.
      // So the title comes from a frame guest that still has the id.
      stripeTitle = guestTask.stripeTitle?.let { Supplier { guestStripeTitleOf(guestTask.id) } },
    )
  }

  /** The stripe title that a frame guest gives the tool window [id], which this class registered for the frame guests. */
  private fun guestStripeTitleOf(id: String): @NlsContexts.TabTitle String {
    val slot = slots.get(id)
    val title = slot?.guests?.firstOrNull()?.stripeTitle
    if (title != null) {
      slot.lastGuestStripeTitle = title
    }
    @Suppress("HardCodedStringLiteral")
    return title ?: slot?.lastGuestStripeTitle ?: id
  }

  /**
   * Tells whether the project of [toolWindow] has an entry: its tool window is available and on the stripe of the project.
   * For the frame owner, only its own available state counts, and a frame owner without content of its own has no entry.
   */
  private fun isEntry(slot: PresentingSlot, toolWindow: ToolWindowImpl): Boolean {
    if (!toolWindow.isOnProjectStripe) {
      return false
    }
    return if (toolWindow === slot.presenting) toolWindow.isOwnAvailable && !manager.suppressesOwnContent else toolWindow.isAvailable
  }

  /** Makes [toolWindow] an entry of the selection or no entry, see [isEntry]. */
  private fun syncEntry(slot: PresentingSlot, toolWindow: ToolWindowImpl) {
    if (isEntry(slot, toolWindow)) {
      slot.selection.add(toolWindow)
    }
    else {
      slot.selection.remove(toolWindow)
    }
    refresh(slot)
  }

  /**
   * Puts the entries of [slot] into the order of the projects of the frame, see [IdeFrameGuests.orderedProjectsOf].
   * The order can change without a change of the entries, so each update of the selection and of the UI asks for it again.
   */
  private fun sortEntries(slot: PresentingSlot) {
    if (slot.selection.entries.size < 2) {
      return
    }
    val projects = IdeFrameGuests.orderedProjectsOf(manager.project)
    slot.selection.sort(compareBy { toolWindow -> projects.indexOf(toolWindow.project).let { if (it < 0) Int.MAX_VALUE else it } })
  }

  /**
   * Brings the tool window of [slot] in line with its entries. With [resolves], a visible tool window without a selected
   * project selects one, see [ToolWindowProjectSelection.resolve].
   */
  private fun refresh(slot: PresentingSlot, resolves: Boolean = true) {
    sortEntries(slot)
    updatePresentingState(slot)
    if (resolves && slot.presenting.isVisible) {
      slot.selection.resolve()
    }
    applySelection(slot)
  }

  /**
   * Gives the presenting tool window the state that the entries of the frame guests ask for. It is available and has its
   * stripe button while a frame guest has an entry. Without such an entry, the frame owner decides both, as in a frame with
   * one project. The tool window comes back when a frame guest gets an entry again, if the removal of the last entry hid it.
   */
  private fun updatePresentingState(slot: PresentingSlot) {
    val presenting = slot.presenting
    if (slot.hasGuestEntry()) {
      presenting.setHasFrameGuestEntry(true)
      if (!presenting.isShowStripeButton) {
        manager.setStripeButtonShown(presenting.id, true)
      }
      if (slot.isHiddenForNoEntry) {
        slot.isHiddenForNoEntry = false
        if (!presenting.isVisible) {
          manager.showToolWindow(presenting.id)
        }
      }
    }
    else if (presenting.hasFrameGuestEntry) {
      val isFrameOwnerOnStripe = presenting.isOwnAvailable && presenting.isOnProjectStripe
      slot.isHiddenForNoEntry = presenting.isVisible && !isFrameOwnerOnStripe
      presenting.setHasFrameGuestEntry(false)
      if (presenting.isAvailable && !isFrameOwnerOnStripe && presenting.isShowStripeButton) {
        // the frame owner left the stripe before, and the last frame guest leaves it now
        manager.hideToolWindow(presenting.id, removeFromStripe = true)
      }
    }
  }

  /** Gives each presented tool window its state: the selected one is visible while the presenting tool window is visible. */
  private fun applySelection(slot: PresentingSlot) {
    val presenting = slot.presenting
    val shown = if (presenting.isVisible) slot.selection.selected else null
    // a hide comes before a show, so a listener never sees two visible tool windows
    // a copy: a listener of a frame guest can remove its tool window
    for (toolWindow in slot.guests.toList()) {
      if (toolWindow !== shown) {
        toolWindow.toolWindowManager.applyPresented(id = toolWindow.id, visible = false, place = presenting.windowInfo)
      }
    }
    if (shown != null && shown !== presenting && shown.presentingSlot === slot) {
      shown.toolWindowManager.applyPresented(id = shown.id, visible = true, place = presenting.windowInfo)
    }
    updateUi(slot)
  }

  /**
   * Puts the selector and the decorator of the shown tool window of a frame guest into the decorator of the presenting tool window.
   * The selector shows only with a choice: a tool window with one project looks as in a frame with one project.
   * A tool window that was never shown has no decorator, and [presentingDecoratorCreated] calls this function when it gets one.
   */
  private fun updateUi(slot: PresentingSlot) {
    val decorator = slot.presenting.decorator ?: return
    sortEntries(slot)
    val entries = slot.selection.entries
    if (entries.size > 1) {
      val selector = slot.selector ?: ToolWindowProjectSelector(manager.project, slot.presenting.disposable) { toolWindow ->
        select(slot, toolWindow)
      }.also { slot.selector = it }
      selector.update(entries, slot.selection.selected)
      decorator.setProjectSelector(selector.component)
    }
    else {
      decorator.setProjectSelector(null)
      // a hidden selector must not hold the tool window of a frame guest that closes
      slot.selector?.update(emptyList(), null)
    }
    decorator.present(shownToolWindowOf(slot.presenting.id)?.getOrCreateDecoratorComponent())
  }

}

/**
 * The tool window of a frame owner that presents the tool windows of the frame guests with its id, and those tool windows.
 * Only [ToolWindowManagerPresentation] reads and writes the state. A presented tool window holds its slot,
 * see [ToolWindowImpl.presentingSlot].
 */
internal class PresentingSlot(
  /** The tool window of the frame owner. Another tool window takes its place when the frame owner registers or unregisters the id. */
  var presenting: ToolWindowImpl,
  /** [ToolWindowManagerPresentation] registered [presenting], because the frame owner had no tool window with the id. */
  var isPlaceholder: Boolean,
  /** The registration of a tool window without content of its own for the id, see [isPlaceholder]. */
  val placeholderTask: RegisterToolWindowTaskData,
) {
  /** The presented tool windows of the frame guests, in the order the frame owner got them. */
  val guests = ArrayList<ToolWindowImpl>()

  /** The tool window whose project the selector selects. [presenting] is the entry of the frame owner. */
  val selection = ToolWindowProjectSelection { activeToolWindow() }

  /** The removal of the last entry hid [presenting]. */
  var isHiddenForNoEntry = false

  /** [presenting] was visible when it left, so the tool window that takes its place shows. */
  var wasVisibleBeforeUnbind = false

  /** A later event selects a project for [presenting], see `ToolWindowManagerPresentation.resolveLater`. */
  var isResolveScheduled = false

  /** The stripe title that a frame guest gave [presenting] last, for the time when no frame guest has the id, see [isPlaceholder]. */
  var lastGuestStripeTitle: @NlsContexts.TabTitle String? = null

  /** The selector of the project. It exists from the first time [presenting] has a choice of projects. */
  var selector: ToolWindowProjectSelector? = null

  /** A frame guest has an entry in the selector. */
  fun hasGuestEntry(): Boolean = selection.entries.any { it !== presenting }

  /** The tool window of the active project of the frame, when the project has one with the id. */
  private fun activeToolWindow(): ToolWindowImpl? {
    val active = IdeFrameGuests.activeProjectOf(presenting.project)
    return if (active === presenting.project) presenting else guests.firstOrNull { it.project === active }
  }

  /** Lets every tool window of a frame guest go. The tool windows of this slot hold no object of each other after the call. */
  fun release() {
    for (toolWindow in guests) {
      toolWindow.presentingSlot = null
    }
    guests.clear()
    selector?.update(emptyList(), null)
  }
}

private fun runOnEdt(action: () -> Unit) {
  if (EDT.isCurrentThreadEdt()) {
    action()
  }
  else {
    ApplicationManager.getApplication().invokeLater(action, ModalityState.any())
  }
}

/** The factory of a tool window of the frame owner that has no content of its own and only presents the tool windows of the frame guests. */
internal object PresentingToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
  }
}
