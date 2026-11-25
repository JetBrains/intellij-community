// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.ide.lightEdit.LightEditService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.reportSoftAssertFailure
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.ref.DebugReflectionUtil
import com.intellij.workspaceModel.ide.EntitiesOrphanage
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Field
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import java.util.function.Predicate
import kotlin.time.Duration.Companion.milliseconds as ms

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
@JvmOverloads
fun testWorkspaceModelLeak(unitTestProject: Project? = null, testClass: Class<*>? = null) {
  if (!CHECK_WORKSPACE_MODEL_LEAKS) {
    return
  }

  doTestWorkspaceModelLeak(unitTestProject, testClass)
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

  // mocks: the interceptor maps of Mockito hold stubbed answers, and an answer may hold test PSI.
  // Block the maps themselves, not each static route to them.
  "org.mockito.internal.util.MockUtil.defaultMockMaker",
  "org.mockito.internal.creation.bytebuddy.InlineDelegateByteBuddyMockMaker.mocks",
  "org.mockito.internal.creation.bytebuddy.MockMethodAdvice.interceptors",

  "com.intellij.testFramework.junit5.showcase.JUnit5ModuleFixtureTest.seenModule", // can be disposed
  "com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightModuleFixtureTest.previousModule",

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

private class LeakReport(val reporter: String) {
  var cleanupReported = false
}

/** Access this map only on the EDT. Weak keys do not keep leaked storage alive. */
private lateinit var reportedStorages: MutableMap<EntityStorage, LeakReport>

private fun brokenCleanupMessage(storagesByReporter: Map<String, List<EntityStorage>>, testClass: Class<*>?): String = buildString {
  appendLine("Found workspace model storage that another check already reported. This is not a new leak.")
  appendLine("The cleanup of the reporting test is broken: it does not release the storage.")
  testClass?.let { appendLine("Found again in test: ${it.name}") }
  for ((reportedBy, instances) in storagesByReporter) {
    appendLine("Reported first by: $reportedBy")
    for (instance in instances) {
      appendLine("  Instance: $instance")
    }
  }
  append("Total already reported storage instances: ${storagesByReporter.values.sumOf { it.size }}")
}

/**
 * Can return duplicates
 */
@OptIn(WorkspaceEntityInternalApi::class)
@TestOnly
private fun Project.collectEntityStorages(): List<EntityStorage> {
  val storages = mutableListOf<EntityStorage>()
  val event = timeoutRunBlocking {
    withTimeoutOrNull(100.ms) {
      workspaceModel.eventLog.first()
    }
  }
  if (event != null) {
    storages += event.storageBefore
    storages += event.storageAfter
    storages += (event as? VersionedStorageChangeInternal)?.getAllChanges().orEmpty().mapNotNull {
      (it.newEntity as? WorkspaceEntityBase)?.snapshot
    }
  }
  storages += workspaceModel.currentSnapshot
  storages.addIfNotNull((workspaceModel as? WorkspaceModelInternal)?.currentSnapshotOfUnloadedEntities)
  storages += service<EntitiesOrphanage>().currentSnapshot
  return storages
}

@TestOnly
private fun doTestWorkspaceModelLeak(unitTestProject: Project? = null, testClass: Class<*>? = null) {
  if (ApplicationManager.getApplication() == null) {
    return
  }

  val shouldExamineField = Predicate<Field?> { field -> field == null || field.declaringClass.name + "." + field.name !in IGNORED_FIELDS }

  while (true) {
    var projects: Set<Project> = emptySet()
    var cleanupFailureName: String? = null
    try {
      runInEdtAndWait {
        if (!::reportedStorages.isInitialized) {
          reportedStorages = CollectionFactory.createWeakIdentityMap(16, 0.75f)
        }
        projects = collectAllNonDisposedProjects(unitTestProject)

        fun collectSnapshots(): List<EntityStorage> = buildList {
          for (project in projects) {
            addAll(project.collectEntityStorages())
          }
          addAll(GlobalWorkspaceModel.getInstancesBlocking().map { it.currentSnapshot })
        }

        val expectedStorages = Collections.newSetFromMap(IdentityHashMap<EntityStorage, Boolean>())
        expectedStorages.addAll(collectSnapshots())

        fun isLeak(storage: EntityStorage): Boolean {
          if (storage in expectedStorages) return false
          expectedStorages.addAll(collectSnapshots())
          return storage !in expectedStorages
        }

        val newLeaks = mutableListOf<EntityStorage>()
        val storagesByReporter = mutableMapOf<String, MutableList<EntityStorage>>()
        var firstLeakDetails: String? = null
        LeakHunter.processLeaks(
          LeakHunter.allRoots(),
          EntityStorage::class.java,
          ::isLeak,
          null,
          shouldExamineField,
        ) { storage, backLink ->
          val report = reportedStorages[storage]
          if (report == null) {
            if (firstLeakDetails == null) {
              firstLeakDetails = LeakHunter.getLeakedObjectDetails(storage, backLink, true)
            }
            newLeaks.add(storage)
          }
          else if (!report.cleanupReported) {
            storagesByReporter.getOrPut(report.reporter) { mutableListOf() }.add(storage)
          }
          true
        }
        val details = firstLeakDetails
        val message = if (details != null) {
          val reporter = testClass?.name ?: "the application teardown check"
          newLeaks.forEach { reportedStorages[it] = LeakReport(reporter) }
          if (newLeaks.size == 1) details else "$details\nTotal new leaked storage instances: ${newLeaks.size}"
        }
        else if (storagesByReporter.isNotEmpty()) {
          cleanupFailureName = "Broken workspace model cleanup in ${storagesByReporter.keys.joinToString()}"
          for (storages in storagesByReporter.values) {
            storages.forEach { reportedStorages.getValue(it).cleanupReported = true }
          }
          brokenCleanupMessage(storagesByReporter, testClass)
        }
        else {
          return@runInEdtAndWait
        }
        println(message)
        println(";-----")
        if (details != null) {
          ThreadUtil.printThreadDump()
        }
        throw AssertionError(message)
      }
      break
    }
    catch (e: AssertionError) {
      val testName = cleanupFailureName ?: run {
        publishHeapDump(LEAKED_WSM)
        testClass?.let { "Workspace model leak in test ${it.name}" } ?: "Workspace model leak"
      }
      reportSoftAssertFailure(testName, e)
      return
    }
    catch (e: ProcessCanceledException) {
      val cause = e.cause
      val projectWasDisposed = projects.any { project ->
        project.isDisposed && (project as? ProjectImpl)?.isTemporarilyDisposed != true
      }
      if (e !is AlreadyDisposedException && cause !is AlreadyDisposedException &&
          !(cause is CancellationException && projectWasDisposed)) {
        publishHeapDump(LEAKED_WSM)
        throw e
      }
      ProgressManager.checkCanceled()
    }
    catch (e: Exception) {
      publishHeapDump(LEAKED_WSM)
      throw AssertionError(e)
    }
  }
}
