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
 * A project that has no page with the id gets an empty page.
 */
@ApiStatus.Internal
open class FrameProjectConfigurable internal constructor(
  private val projects: SettingsFrameProjects,
  private val id: String,
  delegates: Map<Project, Configurable>,
) : SearchableConfigurable, Weighted, ConfigurableProjectProvider, Configurable.NoScroll, Configurable.NoMargin {
  private val delegates = LinkedHashMap(delegates)

  /** The page that gives the name, the weight and the declaration. It stays when its project closes. */
  val representative: Configurable = delegates.values.first()

  /** The component of each project that this page showed. */
  private val pages = LinkedHashMap<Project, JComponent>()
  private var container: JPanel? = null

  /** The page of the selected project, or `null` when that project has no page with the id. */
  val selectedDelegate: Configurable?
    get() = delegates.get(projects.selected)

  private val createdDelegates: List<Pair<Project, Configurable>>
    get() = pages.keys.mapNotNull { project -> delegates.get(project)?.let { project to it } }

  override fun getProject(): Project = projects.selected

  override fun getId(): String = id

  override fun getDisplayName(): @NlsContexts.ConfigurableName String = representative.displayName

  override fun getHelpTopic(): String? = (selectedDelegate ?: representative).helpTopic

  override fun getWeight(): Int = (representative as? Weighted)?.weight ?: 0

  override fun getOriginalClass(): Class<*> {
    val delegate = selectedDelegate ?: representative
    return (delegate as? SearchableConfigurable)?.originalClass ?: delegate.javaClass
  }

  override fun enableSearch(option: String?): Runnable? = (selectedDelegate as? SearchableConfigurable)?.enableSearch(option)

  override fun getPreferredFocusedComponent(): JComponent? {
    val delegate = selectedDelegate ?: return null
    if (projects.selected !in pages) {
      return null
    }
    return ConfigurableWrapper.castIfCreated(Configurable::class.java, delegate)?.preferredFocusedComponent
  }

  override fun focusOn(label: String) {
    selectedDelegate?.focusOn(label)
  }

  /**
   * Returns `null` when the page of the selected project has no component. The Settings editor then shows its default page.
   * The page of the selected project gets its component here and its reset from [reset], outside the read action of this call.
   */
  override fun createComponent(): JComponent? {
    val delegate = selectedDelegate
    val page = if (delegate == null) createEmptyPage() else ConfigurableCardPanel.createDecoratedComponent(delegate) ?: return null
    pages.put(projects.selected, page)
    val panel = JPanel(BorderLayout())
    panel.add(page, BorderLayout.CENTER)
    container = panel
    return panel
  }

  /** Shows the page of the selected project. The first call for a project creates the page of the project. */
  fun showSelectedProject() {
    val container = container ?: return
    val page = pages.getOrPut(projects.selected) {
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
    return JBPanelWithEmptyText().withEmptyText(ApplicationBundle.message("settings.frame.project.no.page", projects.selected.name))
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
