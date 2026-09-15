// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditServiceImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseHandler
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ex.PreparedProjectCloseBatch
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

@TestApplication
@Timeout(30)
internal class ApplicationExitTest {
  private val directory by tempPathFixture()
  private val tracer = TelemetryManager.getInstance().getTracer(Scope("exitApp"))

  @Test
  fun `exit saves prepared projects once before disposal`(): Unit = timeoutRunBlocking {
    withProjects { projects, handler, disposable ->
      val app = ApplicationManager.getApplication()
      val events = mutableListOf<String>()
      val workspaces = projects.map { it.stateStore.storageManager.expandMacro(StoragePathMacros.WORKSPACE_FILE) }
      app.messageBus.connect(disposable).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosingBeforeSave(project: Project) {
          events.add("prepare:${project.name}")
          PropertiesComponent.getInstance(project).setValue("exit.prepared", "true")
        }

        override fun projectClosed(project: Project) {
          assertThat(currentThreadContext()[Job]?.isCancelled).isNotEqualTo(true)
          events.add("close:${project.name}")
        }
      })
      val callerJob = Job((projects.first() as ComponentManagerEx).getCoroutineScope().coroutineContext.job)
      val result = withContext(Dispatchers.EDT) {
        installThreadContext(callerJob, replace = true) {
          saveAndCloseProjectsOnExit(app, tracer,true, true, {
            assertThat(handler.saved).containsExactly(app, *projects.toTypedArray())
            assertThat(events).containsExactlyElementsOf(projects.map { "prepare:${it.name}" })
            events.add("appWillBeClosed")
          }, {
                                       // the auto-save pause ends before the container disposal
                                       assertThat(handler.paused).isZero()
                                       assertThat(currentThreadContext()[Job]?.isCancelled).isNotEqualTo(true)
                                       events.add("finish")
                                       "exit"
                                     })
        }
      }
      assertThat(result).isEqualTo("exit")
      assertThat(callerJob.isCancelled).isTrue()
      assertThat(projects.all { it.isDisposed }).isTrue()
      assertThat(handler.paused).isZero()
      assertThat(events).containsExactlyElementsOf(buildList {
        addAll(projects.map { "prepare:${it.name}" })
        add("appWillBeClosed")
        addAll(projects.map { "close:${it.name}" })
        add("finish")
      })
      for (workspace in workspaces) {
        assertThat(workspace.readText()).contains("exit.prepared")
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `a veto stops disposal unless the exit is forced`(checkCanClose: Boolean): Unit = timeoutRunBlocking {
    withProjects { projects, handler, disposable ->
      ExtensionPointName.create<ProjectCloseHandler>("com.intellij.projectCloseHandler").point
        .registerExtension(ProjectCloseHandler { it !== projects.last() }, disposable)
      var closing = false
      var finished = false
      val result = withContext(Dispatchers.EDT) {
        saveAndCloseProjectsOnExit(ApplicationManager.getApplication(), tracer,true, checkCanClose, {
          closing = true
        }, {
                                     finished = true
                                     "exit"
                                   })
      }
      assertThat(result).isEqualTo(if (checkCanClose) null else "exit")
      // the veto runs before the save, so a vetoed exit writes nothing
      assertThat(handler.saved.isEmpty()).isEqualTo(checkCanClose)
      assertThat(closing).isEqualTo(!checkCanClose)
      assertThat(finished).isEqualTo(!checkCanClose)
      assertThat(projects.all { it.isDisposed }).isEqualTo(!checkCanClose)
      assertThat(ApplicationManager.getApplication().isDisposed).isFalse()
      assertThat(handler.paused).isZero()
    }
  }

  @Test
  fun `disposal and logging failures do not skip the final exit callback`(): Unit = timeoutRunBlocking {
    withProjects { projects, _, _ ->
      var reported = false
      LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<out String?>, t: Throwable?): Set<Action> {
          if (message == "Failed to close and dispose all projects") {
            reported = true
            throw AssertionError("expected logging failure")
          }
          return super.processError(category, message, details, t)
        }
      }).use {
        val result = withContext(Dispatchers.EDT) {
          saveAndCloseProjectsOnExit(ApplicationManager.getApplication(), tracer,true, false, {}, { "restart" }, prepareProjects = { checkCanClose ->
            val batch = checkNotNull(ProjectManagerEx.getInstanceEx().prepareProjectsForExit(checkCanClose))
            object : PreparedProjectCloseBatch by batch {
              override fun close() {
                batch.close()
                throw IOException("expected disposal failure")
              }
            }
          })
        }
        assertThat(result).isEqualTo("restart")
      }
      assertThat(reported).isTrue()
      assertThat(projects.all { it.isDisposed }).isTrue()
    }
  }

  @Test
  fun `a cancelled preparation does not skip project saves or disposal`(): Unit = timeoutRunBlocking {
    withProjects { projects, handler, disposable ->
      val prepared = mutableListOf<Project>()
      val app = ApplicationManager.getApplication()
      app.messageBus.connect(disposable).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosingBeforeSave(project: Project) {
          prepared.add(project)
          if (project === projects.first()) {
            throw ProcessCanceledException()
          }
        }
      })

      val result = withContext(Dispatchers.EDT) {
        saveAndCloseProjectsOnExit(app, tracer, true, false, {}, { "exit" })
      }

      assertThat(result).isEqualTo("exit")
      assertThat(prepared).containsExactlyElementsOf(projects)
      assertThat(handler.saved).containsExactly(app, *projects.toTypedArray())
      assertThat(projects.all { it.isDisposed }).isTrue()
      assertThat(handler.paused).isZero()
    }
  }

  @Test
  fun `a cancelled save in closeAndDisposeAllProjects still disposes every project`(): Unit = timeoutRunBlocking {
    withProjects { projects, handler, _ ->
      handler.saveFailure = ProcessCanceledException()

      val closed = withContext(Dispatchers.EDT) {
        ProjectManagerEx.getInstanceEx().closeAndDisposeAllProjects(checkCanClose = false)
      }

      assertThat(closed).isTrue()
      assertThat(handler.saved).containsExactlyElementsOf(projects)
      assertThat(projects.all { it.isDisposed }).isTrue()
    }
  }

  @Test
  fun `a cancelled caller fails before any project is prepared`(): Unit = timeoutRunBlocking {
    withProjects { projects, handler, disposable ->
      val prepared = mutableListOf<Project>()
      ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosingBeforeSave(project: Project) {
          prepared.add(project)
        }
      })
      val callerJob = Job().apply { cancel() }

      val failure = withContext(Dispatchers.EDT) {
        installThreadContext(callerJob, replace = true) {
          runCatching { ProjectManagerEx.getInstanceEx().closeAndDisposeAllProjects(checkCanClose = false) }.exceptionOrNull()
        }
      }

      assertThat(failure).isInstanceOf(ProcessCanceledException::class.java)
      assertThat(prepared).isEmpty()
      assertThat(handler.saved).isEmpty()
      assertThat(projects.none { it.isDisposed }).isTrue()
    }
  }

  @Test
  fun `a caller cancelled during the preparation stops before the next project`(): Unit = timeoutRunBlocking {
    withProjects { projects, handler, disposable ->
      val callerJob = Job()
      val prepared = mutableListOf<Project>()
      ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosingBeforeSave(project: Project) {
          prepared.add(project)
          if (project === projects.first()) {
            callerJob.cancel()
          }
        }
      })

      val failure = withContext(Dispatchers.EDT) {
        installThreadContext(callerJob, replace = true) {
          runCatching { ProjectManagerEx.getInstanceEx().closeAndDisposeAllProjects(checkCanClose = false) }.exceptionOrNull()
        }
      }

      assertThat(failure).isInstanceOf(ProcessCanceledException::class.java)
      assertThat(prepared).containsExactly(projects.first())
      assertThat(handler.saved).isEmpty()
      assertThat(projects.none { it.isDisposed }).isTrue()
    }
  }

  @Test
  fun `a failing preparation does not skip project saves or disposal`(): Unit = timeoutRunBlocking {
    withProjects { projects, handler, disposable ->
      val app = ApplicationManager.getApplication()
      val expected = IOException("expected preparation failure")
      app.messageBus.connect(disposable).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosingBeforeSave(project: Project) {
          if (project === projects.first()) {
            throw expected
          }
        }
      })

      // by default the message bus logs a subscriber failure instead of rethrowing it; the processor turns the log back into a throw
      val result = LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<out String?>, t: Throwable?): Set<Action> {
          if (t === expected) {
            throw expected
          }
          return super.processError(category, message, details, t)
        }
      }).use {
        withContext(Dispatchers.EDT) {
          saveAndCloseProjectsOnExit(app, tracer, true, false, {}, { "exit" })
        }
      }

      assertThat(result).isEqualTo("exit")
      assertThat(handler.saved).containsExactly(app, *projects.toTypedArray())
      assertThat(projects.all { it.isDisposed }).isTrue()
    }
  }

  @Test
  fun `a cancelled closing listener does not skip the next project`(): Unit = timeoutRunBlocking {
    withProjects { projects, _, disposable ->
      var listenerCalled = false
      ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosing(project: Project) {
          if (project === projects.first()) {
            listenerCalled = true
            throw ProcessCanceledException()
          }
        }
      })
      val result = withContext(Dispatchers.EDT) {
        saveAndCloseProjectsOnExit(ApplicationManager.getApplication(), tracer,true, false, {}, { "exit" })
      }
      assertThat(result).isEqualTo("exit")
      assertThat(listenerCalled).isTrue()
      assertThat(projects.last().isDisposed).isTrue()
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `exit without projects respects the application save flag`(saveApplication: Boolean): Unit = timeoutRunBlocking {
    withProjects(count = 0) { _, handler, _ ->
      val app = ApplicationManager.getApplication()
      val result = withContext(Dispatchers.EDT) {
        saveAndCloseProjectsOnExit(app, tracer,saveApplication, true, {}, { "exit" })
      }
      assertThat(result).isEqualTo("exit")
      assertThat(handler.saved).containsExactlyElementsOf(if (saveApplication) listOf(app) else emptyList())
    }
  }

  @Test
  fun `exit includes the created LightEdit project`(): Unit = timeoutRunBlocking {
    withProjects(count = 0) { _, handler, _ ->
      val app = ApplicationManager.getApplication()
      val lightEdit = withContext(Dispatchers.EDT) {
        (LightEditService.getInstance() as LightEditServiceImpl).getOrCreateProject()
      }
      try {
        assertThat(ProjectManagerEx.getInstanceEx().openProjects).doesNotContain(lightEdit)
        val result = withContext(Dispatchers.EDT) {
          saveAndCloseProjectsOnExit(app, tracer,true, true, {}, { "exit" })
        }
        assertThat(result).isEqualTo("exit")
        assertThat(handler.saved).containsExactly(app, lightEdit)
        assertThat(lightEdit.isDisposed).isTrue()
      }
      finally {
        withContext(NonCancellable + Dispatchers.EDT) {
          PlatformTestUtil.forceCloseProjectWithoutSaving(lightEdit)
        }
      }
    }
  }

  private suspend fun withProjects(
    count: Int = 2,
    action: suspend (List<Project>, RecordingExitSaveHandler, Disposable) -> Unit,
  ) {
    val manager = ProjectManagerEx.getInstanceEx()
    val projects = (0 until count).map { index ->
      val path = directory.resolve(index.toString()).createDirectories()
      checkNotNull(manager.openProject(path, OpenProjectTask {}))
    }
    val disposable = Disposer.newDisposable()
    val handler = RecordingExitSaveHandler()
    ApplicationManager.getApplication().replaceService(SaveAndSyncHandler::class.java, handler, disposable)
    try {
      action(projects, handler, disposable)
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) {
        Disposer.dispose(disposable)
        for (project in projects) {
          PlatformTestUtil.forceCloseProjectWithoutSaving(project)
        }
      }
    }
  }
}

private class RecordingExitSaveHandler : SaveAndSyncHandler() {
  val saved = mutableListOf<ComponentManager>()
  var paused = 0

  /** Thrown after every store of a batch is saved, the way [com.intellij.configurationStore.saveSettingsBatch] propagates a cancellation. */
  var saveFailure: Throwable? = null

  override fun saveSettingsUnderModalProgress(componentManager: ComponentManager): Boolean {
    saved.add(componentManager)
    runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
      componentManager.stateStore.save(forceSavingAllSettings = true)
    }
    return true
  }

  override fun saveSettingsUnderModalProgress(componentManagers: List<ComponentManager>): Boolean {
    val saved = super.saveSettingsUnderModalProgress(componentManagers)
    saveFailure?.let { throw it }
    return saved
  }

  private fun disableAutoSave(): AccessToken {
    paused++
    return object : AccessToken() {
      override fun finish() {
        paused--
      }
    }
  }

  override fun <T> withDisabledAutoSaveBlocking(action: () -> T): T {
    return disableAutoSave().use { action() }
  }

  override suspend fun <T> withDisabledAutoSave(action: suspend CoroutineScope.() -> T): T {
    return disableAutoSave().use { coroutineScope { action() } }
  }

  override fun scheduleSave(task: SaveTask, forceExecuteImmediately: Boolean) {}
  override fun scheduleRefresh() {}
  override fun refreshOpenFiles() {}
  override fun blockSaveOnFrameDeactivation() {}
  override fun unblockSaveOnFrameDeactivation() {}
  override fun blockSyncOnFrameActivation() {}
  override fun unblockSyncOnFrameActivation() {}
  override fun maybeRefresh(modalityState: ModalityState) {}
}
