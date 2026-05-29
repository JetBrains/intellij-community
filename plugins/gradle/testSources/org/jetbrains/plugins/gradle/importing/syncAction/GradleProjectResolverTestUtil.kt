// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.util.application
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncListener
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import java.util.concurrent.CopyOnWriteArrayList

fun whenSyncPhaseCompleted(
  parentDisposable: Disposable,
  action: (ProjectResolverContext, GradleSyncPhase) -> Unit,
) {
  application.messageBus.connect(parentDisposable)
    .subscribe(GradleSyncListener.TOPIC, object : GradleSyncListener {
      override fun onSyncPhaseCompleted(context: ProjectResolverContext, phase: GradleSyncPhase) {
        action(context, phase)
      }
    })
}

fun whenSyncPhaseCompleted(
  phase: GradleSyncPhase,
  parentDisposable: Disposable,
  action: (ProjectResolverContext) -> Unit,
) {
  whenSyncPhaseCompleted(parentDisposable) { context, completedPhase ->
    if (phase == completedPhase) {
      action(context)
    }
  }
}

fun whenModelFetchPhaseCompleted(
  parentDisposable: Disposable,
  action: (ProjectResolverContext, GradleModelFetchPhase) -> Unit,
) {
  application.messageBus.connect(parentDisposable)
    .subscribe(GradleSyncListener.TOPIC, object : GradleSyncListener {
      override fun onModelFetchPhaseCompleted(context: ProjectResolverContext, phase: GradleModelFetchPhase) {
        action(context, phase)
      }
    })
}

fun whenModelFetchCompleted(parentDisposable: Disposable, action: (ProjectResolverContext) -> Unit) {
  application.messageBus.connect(parentDisposable)
    .subscribe(GradleSyncListener.TOPIC, object : GradleSyncListener {
      override fun onModelFetchCompleted(context: ProjectResolverContext) {
        action(context)
      }
    })
}

fun whenProjectLoaded(parentDisposable: Disposable, action: (ProjectResolverContext) -> Unit) {
  application.messageBus.connect(parentDisposable)
    .subscribe(GradleSyncListener.TOPIC, object : GradleSyncListener {
      override fun onProjectLoadedActionCompleted(context: ProjectResolverContext) {
        action(context)
      }
    })
}

fun addSyncContributor(
  phase: GradleSyncPhase,
  parentDisposable: Disposable,
  action: suspend (ProjectResolverContext, ImmutableEntityStorage) -> ImmutableEntityStorage,
) {
  GradleSyncContributor.EP_NAME.point.registerExtension(
    object : GradleSyncContributor {
      override val phase: GradleSyncPhase = phase
      override suspend fun createProjectModel(context: ProjectResolverContext, storage: ImmutableEntityStorage): ImmutableEntityStorage =
        action(context, storage)
    }, parentDisposable)
}

/**
 * Gradle project resolver recreates all extensions for sync.
 * Therefore, this function doesn't allow providing an instance of extension.
 * @see org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.createProjectResolvers
 */
fun ComponentManager.registerProjectResolverExtension(
  projectResolverExtensionClass: Class<out AbstractTestProjectResolverExtension>,
  parentDisposable: Disposable,
  configure: AbstractTestProjectResolverService.() -> Unit,
) {
  val projectResolverExtension = registerProjectResolverExtension(projectResolverExtensionClass, parentDisposable)
  val projectResolverService = registerProjectResolverService(projectResolverExtension.serviceClass, parentDisposable)
  projectResolverService.configure()
}

private fun <T : AbstractTestProjectResolverService> ComponentManager.registerProjectResolverService(
  projectResolverServiceClass: Class<T>,
  parentDisposable: Disposable,
): T {
  val projectResolverService = projectResolverServiceClass.getDeclaredConstructor().newInstance()
  registerOrReplaceServiceInstance(projectResolverServiceClass, projectResolverService, parentDisposable)
  return projectResolverService
}

private fun <T : AbstractTestProjectResolverExtension> registerProjectResolverExtension(
  projectResolverExtensionClass: Class<T>,
  parentDisposable: Disposable,
): T {
  val projectResolverExtension = projectResolverExtensionClass.getDeclaredConstructor().newInstance()
  GradleProjectResolverExtension.EP_NAME.point.registerExtension(projectResolverExtension, parentDisposable)
  return projectResolverExtension
}

abstract class AbstractTestProjectResolverExtension : AbstractProjectResolverExtension() {

  abstract val serviceClass: Class<out AbstractTestProjectResolverService>

  private fun getService(): AbstractTestProjectResolverService {
    val project = resolverCtx.externalSystemTaskId.findProject()!!
    return project.getService(serviceClass)
  }

  override fun getModelProviders(): List<ProjectImportModelProvider> {
    return getService().getModelProviders()
  }
}

abstract class AbstractTestProjectResolverService {

  private val modelProviders = CopyOnWriteArrayList<ProjectImportModelProvider>()

  fun getModelProviders(): List<ProjectImportModelProvider> {
    return modelProviders
  }

  fun addModelProviders(vararg modelProviders: ProjectImportModelProvider) {
    addModelProviders(modelProviders.toList())
  }

  fun addModelProviders(modelProviders: Collection<ProjectImportModelProvider>) {
    this.modelProviders.addAll(modelProviders)
  }
}
