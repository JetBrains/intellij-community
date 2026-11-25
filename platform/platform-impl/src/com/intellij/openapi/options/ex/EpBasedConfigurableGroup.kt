// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.extensions.TechnologyFilter
import com.intellij.openapi.extensions.TechnologyFilterListener
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

/**
 * This class provides logic for handling change in EPs and sends the update signals to the listeners.
 * E.g., EPs can be updated when the settings dialog is open: in this case, we have to update the UI according to the changes.
 */
@ApiStatus.Experimental
internal class EpBasedConfigurableGroup(private val project: Project?, delegate: () -> ConfigurableGroup) : NoScroll, MutableConfigurableGroup, Weighted, SearchableConfigurable, Disposable {
  private val value = SynchronizedClearableLazy(delegate)
  private val listeners = CopyOnWriteArrayList<MutableConfigurableGroup.Listener>()
  private val extendableConfigurableWrappers: MutableList<ConfigurableWrapper> = ArrayList()
  private val configurablesWithEpDependencies: MutableList<WithEpDependencies> = ArrayList()
  private val epListener = createListener()

  /** The extension points of the application that [listenToProject] listens to, by name. */
  private val applicationPointsOfProjects = HashSet<String>()

  override fun getDisplayName(): String = value.value.displayName

  override fun getConfigurables(): Array<Configurable> = value.value.configurables

  override fun getId(): String {
    val value = value.value
    return if (value is SearchableConfigurable) (value as SearchableConfigurable).id else "root"
  }

  override fun createComponent(): JComponent? = null

  override fun isModified(): Boolean = false

  @Synchronized
  override fun addListener(listener: MutableConfigurableGroup.Listener) {
    if (listeners.isEmpty()) {
      val projectOrDefault = project ?: DefaultProjectFactory.getInstance().defaultProject

      Configurable.APPLICATION_CONFIGURABLE.addChangeListener(epListener, this)
      Configurable.PROJECT_CONFIGURABLE.getPoint(projectOrDefault).addChangeListener(epListener, this)
      listenToPoints(extendableConfigurableWrappers, configurablesWithEpDependencies, projectOrDefault, this)

      if (project != null) {
        val appConnection = ApplicationManager.getApplication().messageBus.connect(this)
        appConnection.subscribe(TechnologyFilter.TOPIC, TechnologyFilterListener { changedProject ->
          if (changedProject === project) {
            epListener.run()
          }
        })
      }
    }
    listeners.add(listener)
  }

  /**
   * Listens to the extension points of the pages of [project] in the pages of several projects of the tree, see
   * [FrameProjectConfigurable], until [parentDisposable] is disposed. A change of such a point builds the tree again.
   *
   * The group itself looks at no page inside a page of several projects, also not at a page of its own project.
   * [parentDisposable] must go when [project] closes: a listener keeps its extension point, and the point keeps its project.
   */
  @Synchronized
  fun listenToProject(project: Project, parentDisposable: Disposable) {
    if (project !== this.project) {
      Configurable.PROJECT_CONFIGURABLE.getPoint(project).addChangeListener(epListener, parentDisposable)
    }
    val wrappers = ArrayList<ConfigurableWrapper>()
    val withEpDependencies = ArrayList<WithEpDependencies>()
    ConfigurableVisitor.collect({ configurable ->
      val page = (configurable as? FrameProjectConfigurable)?.pageOf(project)
      if (page != null) {
        collectPage(wrappers, withEpDependencies, page)
      }
    }, value.value.configurables)
    listenToPoints(wrappers, withEpDependencies, project, parentDisposable)
  }

  private fun listenToPoints(
    wrappers: List<ConfigurableWrapper>,
    withEpDependencies: List<WithEpDependencies>,
    project: Project,
    parentDisposable: Disposable,
  ) {
    for (wrapper in wrappers) {
      val ep = wrapper.extensionPoint
      val area = wrapper.project?.extensionArea ?: ApplicationManager.getApplication().extensionArea
      if (ep.childrenEPName != null) {
        area.getExtensionPointIfRegistered<Any>(ep.childrenEPName)?.addChangeListener(epListener, parentDisposable)
      }
      else if (ep.dynamic) {
        val cast = ConfigurableWrapper.cast(WithEpDependencies::class.java, wrapper)
        if (cast != null) {
          for (it in cast.dependencies) {
            listenToPoint(area, it.name, parentDisposable)
          }
        }
      }
      if (ep.providerClass != null) {
        val provider = ep.instantiateConfigurableProvider() as? WithEpDependencies
        if (provider != null) {
          for (it in provider.dependencies) {
            listenToPoint(area, it.name, parentDisposable)
          }
        }
      }
    }
    for (withEpDependency in withEpDependencies) {
      for (it in withEpDependency.dependencies) {
        listenToPoint(project.extensionArea, it.name, parentDisposable)
      }
    }
  }

  /** Listens to the point [name] of [area], or to the point of the application when [area] has no such point. */
  private fun listenToPoint(area: ExtensionsArea, name: String, parentDisposable: Disposable) {
    if (parentDisposable === this || area.hasExtensionPoint(name)) {
      findExtensionPoint(area, name).addChangeListener(epListener, parentDisposable)
    }
    else if (applicationPointsOfProjects.add(name)) {
      // the pages of several projects name one point of the application: one listener builds the tree once,
      // and it must not go with the first project that closes
      findExtensionPoint(area, name).addChangeListener(epListener, this)
    }
  }

  override fun apply() {}

  override fun getWeight(): Int {
    val value = value.value
    return if (value is Weighted) (value as Weighted).weight else 0
  }

  private fun createListener(): Runnable {
    return Runnable {
      value.drop()
      extendableConfigurableWrappers.clear()
      configurablesWithEpDependencies.clear()
      collect(extendableConfigurableWrappers, configurablesWithEpDependencies, value.value.configurables)

      for (listener in listeners) {
        listener.handleUpdate()
      }
    }
  }

  override fun dispose() {
    value.drop()
    listeners.clear()
  }

  init {
    collect(extendableConfigurableWrappers, configurablesWithEpDependencies, value.value.configurables)
  }
}

private fun findExtensionPoint(area: ExtensionsArea, name: String): ExtensionPoint<Any> {
  if (area.hasExtensionPoint(name)) {
    return area.getExtensionPoint(name)
  }
  else {
    return ApplicationManager.getApplication().extensionArea.getExtensionPoint(name)
  }
}

@ApiStatus.Internal
private fun collect(configurableWrappers: MutableList<ConfigurableWrapper>, configurablesWithEpDependencies: MutableList<WithEpDependencies>, configurables: Array<Configurable>) {
  for (configurable in configurables) {
    collectPage(configurableWrappers, configurablesWithEpDependencies, configurable)
    if (configurable !is Configurable.Composite) {
      continue
    }

    val children: Array<Configurable> = try {
      configurable.configurables
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      ConfigurableWrapper.LOG.error("Cannot get children $configurable", e)
      continue
    }
    collect(configurableWrappers, configurablesWithEpDependencies, children)
  }
}

/** Adds [configurable] to the list that fits it when its presence or its child pages depend on an extension point. */
private fun collectPage(
  configurableWrappers: MutableList<ConfigurableWrapper>,
  configurablesWithEpDependencies: MutableList<WithEpDependencies>,
  configurable: Configurable,
) {
  if (configurable is ConfigurableWrapper) {
    val ep = configurable.extensionPoint
    if (ep.childrenEPName != null || ep.dynamic) {
      configurableWrappers.add(configurable)
    }
    if (configurable.providerClass != null) {
      val providerClass = ep.findClassOrNull(configurable.providerClass)
      if (providerClass != null) {
        if (WithEpDependencies::class.java.isAssignableFrom(providerClass)) {
          configurableWrappers.add(configurable)
        }
      }
    }
  }
  else if (configurable is WithEpDependencies) {
    configurablesWithEpDependencies.add(configurable)
  }
}
