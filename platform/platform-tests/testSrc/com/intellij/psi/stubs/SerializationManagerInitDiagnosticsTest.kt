// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks the diagnostics of [SerializationManagerImpl] for a serializer initialization that a cancellation interrupts.
 */
@TestApplication
internal class SerializationManagerInitDiagnosticsTest {
  @TempDir
  lateinit var tempDir: Path

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `an interrupted initialization is recorded and the next attempt succeeds`() {
    val cancelOnce = AtomicBoolean(true)
    registerParserDefinition(TestParserDefinition {
      if (isTestCall.get() && cancelOnce.getAndSet(false)) throw ProcessCanceledException()
      fileElementType
    })

    withSerializationManager { manager ->
      assertEquals("", manager.initDiagnostics)

      assertThrows(ProcessCanceledException::class.java) { initFromTest(manager) }
      val diagnostics = manager.initDiagnostics
      assertTrue(diagnostics.contains("interrupted 1 time(s)"), diagnostics)
      assertTrue(diagnostics.contains("com.intellij.lang.parserDefinition[${TestLanguage.id}]"), diagnostics)

      initFromTest(manager)
    }
  }

  @Test
  fun `a cancelled static initializer is reported once for each manager`() {
    // ProgressManager.checkCanceled does nothing until the service exists
    ProgressManager.getInstance()
    // only the static initializer runs in a cancelled context, so the loop over the other extensions does not stop first
    val cancelledJob = Job().apply { cancel() }
    registerParserDefinition(TestParserDefinition {
      if (!isTestCall.get()) fileElementType
      else installThreadContext(cancelledJob, replace = true) { CancelledInitHolder.fileElementType }
    })

    val firstErrors = collectLoggedErrors {
      withSerializationManager { manager ->
        assertThrows(ExceptionInInitializerError::class.java) { initFromTest(manager) }
        assertThrows(NoClassDefFoundError::class.java) { initFromTest(manager) }
      }
    }
    assertCancelledInitializerReported(firstErrors)

    // a new manager sees only the NoClassDefFoundError, which keeps the first failure as a copy without a cause
    val secondErrors = collectLoggedErrors {
      withSerializationManager { manager ->
        assertThrows(NoClassDefFoundError::class.java) { initFromTest(manager) }
      }
    }
    assertCancelledInitializerReported(secondErrors)
  }

  /**
   * Other threads, such as the serializer initialization of the application, can also call the test parser definitions.
   * The definitions change their behavior only for a call from this function.
   */
  private fun initFromTest(manager: SerializationManagerImpl) {
    isTestCall.set(true)
    try {
      manager.initSerializers()
    }
    finally {
      isTestCall.set(false)
    }
  }

  private fun collectLoggedErrors(action: () -> Unit): List<String> {
    val errors = Collections.synchronizedList(ArrayList<String>())
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        errors.add(message)
        return Action.NONE
      }
    }
    LoggedErrorProcessor.executeWith<Throwable>(processor) { action() }
    return errors
  }

  private fun assertCancelledInitializerReported(errors: List<String>) {
    assertEquals(1, errors.size, errors.toString())
    val message = errors.single()
    assertTrue(message.contains(CancelledInitHolder::class.java.name), message)
    assertTrue(message.contains("com.intellij.lang.parserDefinition[${TestLanguage.id}]"), message)
  }

  private fun registerParserDefinition(definition: ParserDefinition) {
    val extension = LanguageExtensionPoint<ParserDefinition>(TestLanguage.id, definition)
    extension.pluginDescriptor = DefaultPluginDescriptor("SerializationManagerInitDiagnosticsTest")
    LanguageParserDefinitions.INSTANCE.point!!.registerExtension(extension, disposable)
  }

  private fun withSerializationManager(action: (SerializationManagerImpl) -> Unit): Unit = timeoutRunBlocking {
    val scope = childScope("SerializationManagerInitDiagnosticsTest")
    val manager = SerializationManagerImpl(tempDir.resolve("rep.names"), false, scope)
    try {
      action(manager)
    }
    finally {
      Disposer.dispose(manager)
      scope.cancel()
    }
  }
}

private object TestLanguage : Language("SerializationManagerInitDiagnosticsTest")

private val isTestCall = ThreadLocal.withInitial { false }

private val fileElementType = IFileElementType(TestLanguage)

private object CancelledInitHolder {
  val fileElementType: IFileElementType

  init {
    ProgressManager.checkCanceled()
    fileElementType = IFileElementType("CancelledInitHolder", TestLanguage)
  }
}

private class TestParserDefinition(private val fileNodeType: () -> IFileElementType) : ParserDefinition {
  override fun getFileNodeType(): IFileElementType = fileNodeType()

  override fun createLexer(project: Project?): Lexer = throw UnsupportedOperationException()

  override fun createParser(project: Project?): PsiParser = throw UnsupportedOperationException()

  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

  override fun createElement(node: ASTNode?): PsiElement = throw UnsupportedOperationException()

  override fun createFile(viewProvider: FileViewProvider): PsiFile = throw UnsupportedOperationException()
}
