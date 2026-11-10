// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.InjectedDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.TokenType
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent

@TestApplication
internal class DataValidatorsTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `an injected context rejects cached PSI after invalidation`(provideInjectedPsi: Boolean): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val virtualFile = LightVirtualFile("sample.txt", PlainTextFileType.INSTANCE, "text")
    val file = readAction {
      requireNotNull(PsiManager.getInstance(project).findFile(virtualFile))
    }
    val element = readAction { file.firstChild }
    val context = withContext(Dispatchers.EDT) {
      Utils.createAsyncDataContext(object : JComponent(), UiDataProvider {
        override fun uiDataSnapshot(sink: DataSink) {
          sink.lazy(CommonDataKeys.PSI_FILE) { file }
          sink.lazy(CommonDataKeys.PSI_ELEMENT) { element }
          if (provideInjectedPsi) {
            sink.lazy(InjectedDataKeys.PSI_FILE) { file }
            sink.lazy(InjectedDataKeys.PSI_ELEMENT) { element }
          }
        }
      })
    }
    val injected = AnActionEvent.getInjectedDataContext(context)
    readAction {
      assertSame(file, context.getData(CommonDataKeys.PSI_FILE))
      assertSame(element, context.getData(CommonDataKeys.PSI_ELEMENT))
      assertSame(file, injected.getData(CommonDataKeys.PSI_FILE))
      assertSame(element, injected.getData(CommonDataKeys.PSI_ELEMENT))
    }
    withContext(Dispatchers.EDT) {
      writeAction { PsiManagerEx.getInstanceEx(project).fileManagerEx.forceReload(virtualFile) }
    }
    readAction {
      assertFalse(file.isValid)
      assertFalse(element.isValid)
      assertNull(context.getData(CommonDataKeys.PSI_FILE))
      assertNull(context.getData(CommonDataKeys.PSI_ELEMENT))
      assertNull(injected.getData(CommonDataKeys.PSI_FILE))
      assertNull(injected.getData(CommonDataKeys.PSI_ELEMENT))
    }
  }

  @Test
  fun `a data context uses validators added during a cache rebuild`(): Unit = timeoutRunBlocking {
    val element = LeafPsiElement(TokenType.BAD_CHARACTER, "invalid")
    val context = withContext(Dispatchers.EDT) {
      CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
        sink.lazy(VALIDATED_KEY) { element }
      }
    }
    readAction { assertSame(element, context.getData(VALIDATED_KEY)) }

    val entered = CompletableDeferred<Unit>()
    val release = CountDownLatch(1)
    val builderThread = AtomicReference<Thread>()
    DataValidators.EP_NAME.point.registerExtension(object : DataValidators() {
      override fun collectValidators(registry: ValidatorRegistry) {
        registry.register(WARMUP_KEY) { _, _, _ -> true }
        if (Thread.currentThread() == builderThread.get()) {
          entered.complete(Unit)
          assertTrue(release.await(10, TimeUnit.SECONDS))
        }
      }
    }, disposable)

    val building = async(Dispatchers.Default) {
      builderThread.set(Thread.currentThread())
      DataValidators.validOrNull("warmup", WARMUP_KEY.name, this@DataValidatorsTest)
    }
    try {
      entered.await()
      DataValidators.EP_NAME.point.registerExtension(object : DataValidators() {
        override fun collectValidators(registry: ValidatorRegistry) {
          registry.register(VALIDATED_KEY) { value, _, _ -> value.isValid }
        }
      }, disposable)
    }
    finally {
      release.countDown()
    }
    building.await()

    readAction { assertNull(context.getData(VALIDATED_KEY)) }
  }

  companion object {
    private val projectFixture = projectFixture()
    private val WARMUP_KEY = DataKey.create<String>("DataValidatorsTest.warmup")
    private val VALIDATED_KEY = DataKey.create<PsiElement>("DataValidatorsTest.validated")
  }
}
