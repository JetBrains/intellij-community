// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.ex

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.newEditor.ConfigurableProjectProvider
import com.intellij.openapi.options.newEditor.SettingsFrameProjects
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBPanelWithEmptyText
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * One project-level page of a Settings window that configures several projects, see [SettingsFrameProjects].
 *
 * The Settings tree holds one such page for the pages with one id of all the projects. The page shows the settings of the
 * selected project. It keeps the page of each project that it showed, so a change of a project waits for Apply while the
 * window shows another project. [isModified], [apply], [reset] and [cancel] cover the pages of all the projects.
 *
 * The page shows only the projects that have a page with the id, see [projectsWithPage]. When the selected project has no
 * such page, the page shows the first project that has one, and the selection stays.
 */
@ApiStatus.Internal
open class FrameProjectConfigurable internal constructor(
  private val projects: SettingsFrameProjects,
  private val id: String,
  delegates: Map<Project, Configurable>,
) : SearchableConfigurable, Weighted, ConfigurableProjectProvider, Configurable.NoScroll, Configurable.NoMargin {
  private val delegates = LinkedHashMap(delegates)

  // the page stores its name and its weight, because the page of the first project goes when that project closes
  private val name: @NlsContexts.ConfigurableName String = delegates.values.first().displayName
  private val weight: Int = (delegates.values.first() as? Weighted)?.weight ?: 0

  /** The owner of the frame of the Settings window. It lives as long as the window. */
  val frameOwner: Project
    get() = projects.frameOwner

  /**
   * The page that holds the declaration of this page: the page of the first project that has one.
   * It is `null` when no project has a page with the id.
   */
  val declaration: Configurable?
    get() = delegates.values.firstOrNull()

  /** The component of each project that this page showed. */
  private val pages = LinkedHashMap<Project, JComponent>()
  private var container: JPanel? = null

  /** The projects of the selector that have a page with the id, in the order of the selector. */
  val projectsWithPage: List<Project>
    get() = projects.projects.filter { it in delegates }

  /**
   * The project that the page shows: the selected project when it has a page with the id, else the first of [projectsWithPage].
   * When no project has a page with the id, it is the selected project, and the page is empty.
   */
  val shownProject: Project
    get() = projects.selected.takeIf { it in delegates } ?: projectsWithPage.firstOrNull() ?: projects.selected

  /** The page of [shownProject], or `null` when no project has a page with the id. */
  val selectedDelegate: Configurable?
    get() = delegates.get(shownProject)

  private val createdDelegates: List<Pair<Project, Configurable>>
    get() = pages.keys.mapNotNull { project -> delegates.get(project)?.let { project to it } }

  override fun getProject(): Project = shownProject

  override fun getId(): String = id

  override fun getDisplayName(): @NlsContexts.ConfigurableName String = name

  override fun getHelpTopic(): String? = selectedDelegate?.helpTopic

  override fun getWeight(): Int = weight

  override fun getOriginalClass(): Class<*> {
    val delegate = selectedDelegate ?: return javaClass
    return (delegate as? SearchableConfigurable)?.originalClass ?: delegate.javaClass
  }

  override fun enableSearch(option: String?): Runnable? = (selectedDelegate as? SearchableConfigurable)?.enableSearch(option)

  override fun getPreferredFocusedComponent(): JComponent? {
    val delegate = selectedDelegate ?: return null
    if (shownProject !in pages) {
      return null
    }
    return ConfigurableWrapper.castIfCreated(Configurable::class.java, delegate)?.preferredFocusedComponent
  }

  override fun focusOn(label: String) {
    selectedDelegate?.focusOn(label)
  }

  /**
   * Returns `null` when the page of [shownProject] has no component. The Settings editor then shows its default page.
   * The page of [shownProject] gets its component here and its reset from [reset], outside the read action of this call.
   */
  override fun createComponent(): JComponent? {
    val delegate = selectedDelegate
    val page = if (delegate == null) createEmptyPage() else ConfigurableCardPanel.createDecoratedComponent(delegate) ?: return null
    pages.put(shownProject, page)
    val panel = JPanel(BorderLayout())
    panel.add(page, BorderLayout.CENTER)
    container = panel
    return panel
  }

  /** Shows the page of [shownProject]. The first call for a project creates the page of the project. */
  fun showSelectedProject() {
    val container = container ?: return
    val page = pages.getOrPut(shownProject) {
      selectedDelegate?.let { ConfigurableCardPanel.createConfigurableComponent(it) } ?: createEmptyPage()
    }
    if (container.componentCount == 1 && container.getComponent(0) === page) {
      return
    }
    container.removeAll()
    container.add(page, BorderLayout.CENTER)
    container.revalidate()
    container.repaint()
  }

  override fun isModified(): Boolean = createdDelegates.any { it.second.isModified }

  /** Applies the modified page of each project. A project whose page fails becomes the selected project, so the user sees that page. */
  @Throws(ConfigurationException::class)
  override fun apply() {
    for ((project, delegate) in createdDelegates) {
      if (!delegate.isModified) {
        continue
      }
      try {
        delegate.apply()
      }
      catch (e: ConfigurationException) {
        projects.select(project)
        throw e
      }
    }
  }

  override fun reset() {
    for ((_, delegate) in createdDelegates) {
      delegate.reset()
    }
  }

  override fun cancel() {
    for ((_, delegate) in createdDelegates) {
      delegate.cancel()
    }
  }

  override fun disposeUIResources() {
    for ((_, delegate) in createdDelegates) {
      delegate.disposeUIResources()
    }
    pages.clear()
    container = null
  }

  /** `true` when the page of [project] has a change that waits for Apply. */
  fun isModified(project: Project): Boolean = createdDelegateOf(project)?.isModified == true

  /** Applies the page of [project] when it has a change. A failed page makes [project] the selected project, as [apply] does. */
  @Throws(ConfigurationException::class)
  fun apply(project: Project) {
    val delegate = createdDelegateOf(project) ?: return
    if (!delegate.isModified) {
      return
    }
    try {
      delegate.apply()
    }
    catch (e: ConfigurationException) {
      projects.select(project)
      throw e
    }
  }

  /** Drops the change of the page of [project], and shows the saved values again. */
  fun cancel(project: Project) {
    val delegate = createdDelegateOf(project) ?: return
    delegate.cancel()
    delegate.reset()
  }

  /** Adds the page of [project], because [project] joins the Settings window. A project that has a page keeps it. */
  internal fun addDelegate(project: Project, delegate: Configurable) {
    delegates.putIfAbsent(project, delegate)
  }

  private fun createdDelegateOf(project: Project): Configurable? = if (project in pages) delegates.get(project) else null

  /** Drops the page of [project], because the project closes. A change of that page that waits for Apply is lost. */
  internal fun forget(project: Project) {
    val delegate = delegates.remove(project)
    val page = pages.remove(project) ?: return
    delegate?.disposeUIResources()
    container?.remove(page)
  }

  private fun createEmptyPage(): JComponent {
    return JBPanelWithEmptyText().withEmptyText(ApplicationBundle.message("settings.frame.project.no.page", shownProject.name))
  }

  override fun toString(): String = displayName
}

/** A [FrameProjectConfigurable] with child pages. [children] holds the child pages of all the projects. */
internal class FrameProjectCompositeConfigurable(
  projects: SettingsFrameProjects,
  id: String,
  delegates: Map<Project, Configurable>,
  /** A project that joins the Settings window adds its child pages here, see [FrameProjectsConfigurableGroup.join]. */
  var children: Array<Configurable>,
) : FrameProjectConfigurable(projects, id, delegates), Configurable.Composite {
  override fun getConfigurables(): Array<Configurable> = children
}
