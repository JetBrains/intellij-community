// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger

import com.intellij.openapi.application.*
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Ignore("Manually run only")
@RunWith(JUnit4::class)
class ReadWriteLockStatusManualTest : HeavyPlatformTestCase() {
  @Test
  fun testReadLock() {
    AppExecutorUtil.getAppExecutorService().submit {
      runReadAction {
        val x = 1
      }

      ReadAction.run<RuntimeException> {
        val x = 1
      }

      ReadAction.nonBlocking {
        val x = 1
      }.executeSynchronously()

      runReadAction {
        val x = 1
        runReadAction {
          val y = 1
        }
      }
    }.get()
  }

  @Test
  fun testCoroutinesReadAction() {
    runBlocking {
      readAction {
        val x = 1
      }
    }
  }

  @Test
  fun testWriteLock() {
    WriteAction.run<RuntimeException> {
      val x = 1
    }
    runWriteAction {
      val x = 1
    }
  }

  @Test
  fun testCoroutinesWriteAction() {
    AppExecutorUtil.getAppExecutorService().submit {
      runBlocking {
        edtWriteAction {
          val x = 1
        }
      }
    }
  }
}
