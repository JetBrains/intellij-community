// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.unregisterService
import com.intellij.util.io.BaseOutputReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

@TestApplication
@EnabledOnOs(OS.WINDOWS)
internal class KillProcessTreeCanceledCallerTest {
  @Test
  fun `a canceled caller kills the process tree before the process service is created`(@TestDisposable disposable: Disposable) {
    destroyUnderCanceledIndicator(disposable) { process ->
      object : OSProcessHandler(process, "ping") {
        // The default calls `hasPty()`, which creates the service.
        override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.BLOCKING
      }
    }
  }

  @Test
  fun `a canceled caller kills a killable process before the process service is created`(@TestDisposable disposable: Disposable) {
    destroyUnderCanceledIndicator(disposable) { process ->
      object : KillableProcessHandler(process, "ping") {
        override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.BLOCKING
      }
    }
  }

  private fun destroyUnderCanceledIndicator(disposable: Disposable, createHandler: (Process) -> OSProcessHandler) {
    val application = ApplicationManager.getApplication()
    val previous = LocalProcessService.getInstance()
    // The rollback of `replaceService` restores the registration of the module.
    application.replaceService(LocalProcessService::class.java, previous, disposable)
    // The first lookup must create the service, so the instance of `replaceService` gives way to a lazy registration.
    application.unregisterService(LocalProcessService::class.java)
    (application as ComponentManagerEx).registerService(LocalProcessService::class.java, previous.javaClass,
                                                        ComponentManagerImpl.fakeCorePluginDescriptor, false)
    // Disposed before the rollback, which expects its own instance in place.
    Disposer.register(disposable) { application.registerServiceInstance(LocalProcessService::class.java, previous) }
    val process = ProcessBuilder("ping", "-n", "300", "127.0.0.1").start()
    val handler = createHandler(process)
    handler.startNotify()
    try {
      val indicator = EmptyProgressIndicator().apply { cancel() }
      ProgressManager.getInstance().executeProcessUnderProgress({ handler.destroyProcess() }, indicator)

      assertThat(handler.waitFor(30_000)).isTrue()
      assertThat(process.isAlive).isFalse()
    }
    finally {
      process.destroyForcibly()
    }
  }
}
