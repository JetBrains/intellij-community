// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.ide.lightEdit.LightEditService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.ref.DebugReflectionUtil
import com.intellij.workspaceModel.ide.EntitiesOrphanage
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Field
import java.util.IdentityHashMap
import java.util.function.Predicate

/**
 * Check for workspace model snapshot leak after every unit test
 */
private val CHECK_WORKSPACE_MODEL_LEAKS = System.getProperty("idea.test.workspaceModel.leaks", "false")!!.toBoolean()

/**
 * To avoid false positives, search for non-disposed projects in LeakHunter roots, to find their current workspace model snapshot.
 * Useful only in unit tests, because they may not be in ProjectManager.openProjects
 */
private val SEARCH_PROJECT_WITH_LEAK_HUNTER =
  System.getProperty("idea.test.workspaceModel.leaks.searchProjectWithLeakHunter", "true")!!.toBoolean()

/**
 * Check for workspace model snapshot leak after application teardown and after test suite
 */
private val CHECK_WORKSPACE_MODEL_LEAKS_ON_APPLICATION_TEARDOWN =
  System.getProperty("idea.test.workspaceModel.leaks.onApplicationTeardown", "false")!!.toBoolean()

@TestOnly
private fun collectAllNonDisposedProjects(unitTestProject: Project?): Set<Project> {
  val projectManager = ProjectManagerEx.getInstanceEx()
  val openProjects = mutableSetOf<Project?>()

  if (SEARCH_PROJECT_WITH_LEAK_HUNTER) {
    DebugReflectionUtil.walkObjects(
      1_000,
      1_000_000,
      LeakHunter.allRoots().get(),
      ProjectImpl::class.java,
      { true },
    ) { project, _ ->
      openProjects.add(project)
      true
    }
  }

  openProjects.addAll(projectManager.openProjects)

  if (projectManager.isDefaultProjectInitialized) {
    openProjects.add(projectManager.defaultProject)
  }
  openProjects.add(unitTestProject)
  openProjects.add(LightEditService.getInstance().project)

  return openProjects.filterNotNull().filter { it.isDisposed.not() || ((it as? ProjectImpl)?.isTemporarilyDisposed == true) }.toSet()
}

@ApiStatus.Internal
@TestOnly
fun testWorkspaceModelLeak(unitTestProject: Project? = null) {
  if (!CHECK_WORKSPACE_MODEL_LEAKS) {
    return
  }

  doTestWorkspaceModelLeak(unitTestProject)
}

@ApiStatus.Internal
@TestOnly
fun testWorkspaceModelLeakOnApplicationTeardown(unitTestProject: Project? = null) {
  if (!CHECK_WORKSPACE_MODEL_LEAKS_ON_APPLICATION_TEARDOWN) {
    return
  }

  doTestWorkspaceModelLeak(unitTestProject)
}

private val IGNORED_FIELDS = setOf(
  "com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModelRegistry.environmentToModel",

  // expected
  "com.intellij.testFramework.HeavyPlatformTestCase.myModule", // when module is disposed, it contains and old wsm snapshot
  "com.intellij.testFramework.HeavyPlatformTestCase.myEditorListenerTracker",
  "com.intellij.testFramework.HeavyPlatformTestCase.myProject",

  "com.intellij.testFramework.junit5.showcase.JUnit5ModuleFixtureTest.seenModule", // can be disposed

  // cached value
  "com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge.modelValue",

  "com.intellij.lang.javascript.config.graph.JSConfigGraphCache.cs", // flow inside a coroutine

  "com.intellij.util.messages.impl.MessageBusImpl.subscribers",
  "com.intellij.util.messages.impl.BaseBusConnection.subscriptions",

  "kotlinx.coroutines.internal.DispatchedContinuation.continuation", // this is kind of local variable, an it is kind of ok?
  "kotlinx.coroutines.ChildHandleNode.childJob",
  $$"kotlinx.coroutines.JobSupport._state$volatile",
  $$"kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.currentTask",
  "kotlinx.coroutines.CancellableContinuationImpl.context",
  // JUnit root store keeps memoized default-creator closures after values are computed, and they may retain stale test instances.
  $$"org.junit.platform.engine.support.store.NamespacedHierarchicalStore$MemoizingSupplier.delegate",
  "com.intellij.testFramework.junit5.fixture.TestContextImpl.context",
  $$$"com.intellij.testFramework.junit5.fixture.TestFixtureExtension$before$lambda$0$$inlined$CoroutineExceptionHandler$1.$context$inlined",
  "org.junit.jupiter.engine.descriptor.AbstractExtensionContext.testDescriptor",
  "org.junit.jupiter.engine.descriptor.TestTemplateInvocationTestDescriptor.invocationContext",
  "org.junit.jupiter.params.ParameterizedInvocationContext.arguments",
  "org.junit.jupiter.params.EvaluatedArgumentSet.all",
  "org.junit.vintage.engine.descriptor.RunnerTestDescriptor.runner",

  "com.intellij.platform.workspace.storage.impl.ImmutableEntityStorageImpl.EMPTY", // static field, always alive
  "com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl.updatesFlow", // may have old events, not yet handled by all listeners
  "com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl.eventLog",
  "com.intellij.platform.workspace.storage.impl.VersionedEntityStorageImpl.currentSnapshot", // this is a cache
)

private const val LEAKED_WSM = "leakedWorkspaceModel"

@TestOnly
private fun doTestWorkspaceModelLeak(unitTestProject: Project? = null) {
  if (ApplicationManager.getApplication() == null) {
    return
  }

  val shouldExamineField = Predicate<Field?> { field -> field == null || field.declaringClass.name + "." + field.name !in IGNORED_FIELDS }

  runInEdtAndWait {
    val projects: Set<Project> = collectAllNonDisposedProjects(unitTestProject)

    val expectedStorages: Set<EntityStorage> = run {
      val realStorages = mutableListOf<EntityStorage>()

      for (project in projects) {
        realStorages += project.workspaceModel.currentSnapshot
        realStorages.addIfNotNull((project.workspaceModel as? WorkspaceModelInternal)?.currentSnapshotOfUnloadedEntities)
        realStorages += project.service<EntitiesOrphanage>().currentSnapshot
      }
      realStorages += GlobalWorkspaceModel.getInstancesBlocking().map { it.currentSnapshot }

      IdentityHashMap(realStorages.associateWith { Unit }).keys
    }

    try {
      LeakHunter.checkLeak(LeakHunter.allRoots(), EntityStorage::class.java, shouldExamineField) {
        it !in expectedStorages
      }
    }
    catch (e: AssertionError) {
      publishHeapDump(LEAKED_WSM)
      throw AssertionError(e)
    }
    catch (e: Exception) {
      publishHeapDump(LEAKED_WSM)
      throw AssertionError(e)
    }
  }
}