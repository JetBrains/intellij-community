// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.*
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lexer.LexerBase
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.Icon

private const val PREFIX_LENGTH = 4

class HostToInjectedUnescapedTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    (FileTypeManager.getInstance() as FileTypeManagerImpl).registerFileType(
      FixedPrefixFileType.INSTANCE,
      listOf(ExtensionFileNameMatcher(FixedPrefixFileType.INSTANCE.defaultExtension)),
      testRootDisposable,
      PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!
    )
    LanguageParserDefinitions.INSTANCE.addExplicitExtension(
      FixedPrefixLanguage, FixedPrefixParserDefinition(), testRootDisposable
    )
    InjectedLanguageManager.getInstance(project)
      .registerMultiHostInjector(FixedPrefixInjector(), testRootDisposable)
  }

  fun testHostToInjectedUnescaped() {
    assertHostToInjected("PRFXabc",
      4 to 0,
      5 to 1,
      6 to 2,
      7 to 3,
    )
  }

  private fun assertHostToInjected(hostText: String, vararg mappings: Pair<Int, Int>) {
    val psiFile = myFixture.configureByText(FixedPrefixFileType.INSTANCE, hostText)
    val host = PsiTreeUtil.findChildOfType(psiFile, FixedPrefixHost::class.java)!!

    var injectedFile: PsiFile? = null
    InjectedLanguageManager.getInstance(project).enumerate(host) { psi, _ -> injectedFile = psi }
    assertNotNull("Injection should be created", injectedFile)

    val documentWindow = InjectedLanguageUtil.getDocumentWindow(injectedFile!!)!!
    for ((hostOffset, expectedInjected) in mappings) {
      assertEquals(
        "hostOffset=$hostOffset",
        expectedInjected,
        InjectedLanguageUtil.hostToInjectedUnescaped(documentWindow, hostOffset)
      )
    }
  }
}

private object FixedPrefixLanguage : Language("FixedPrefix")

private class FixedPrefixFileType private constructor() : LanguageFileType(FixedPrefixLanguage) {
  override fun getName(): String = "FixedPrefix"
  override fun getDescription(): String = "Fixed prefix test file type"
  override fun getDefaultExtension(): String = "fxpfx"
  override fun getIcon(): Icon? = null

  companion object {
    @JvmField
    val INSTANCE = FixedPrefixFileType()
  }
}

private class FixedPrefixFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, FixedPrefixLanguage) {
  override fun getFileType() = FixedPrefixFileType.INSTANCE
}

private object FixedPrefixFileElementType : IFileElementType("FixedPrefixFile", FixedPrefixLanguage)
private val FIXED_PREFIX_TOKEN = IElementType("FIXED_PREFIX_TOKEN", FixedPrefixLanguage)
private val FIXED_PREFIX_CONTENT = IElementType("FIXED_PREFIX_CONTENT", FixedPrefixLanguage)

private class FixedPrefixLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var end = 0
  private var position = 0

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    this.end = endOffset
    this.position = startOffset
  }

  override fun getState() = 0
  override fun getTokenType() = if (position < end) FIXED_PREFIX_TOKEN else null
  override fun getTokenStart() = position
  override fun getTokenEnd() = end
  override fun advance() { position = end }
  override fun getBufferSequence() = buffer
  override fun getBufferEnd() = end
}

private class FixedPrefixParser : PsiParser {
  override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
    val fileMarker = builder.mark()
    if (!builder.eof()) {
      val contentMarker = builder.mark()
      builder.advanceLexer()
      contentMarker.done(FIXED_PREFIX_CONTENT)
    }
    fileMarker.done(root)
    return builder.treeBuilt
  }
}

private class FixedPrefixParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?) = FixedPrefixLexer()
  override fun createParser(project: Project?) = FixedPrefixParser()
  override fun getFileNodeType() = FixedPrefixFileElementType
  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY
  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

  override fun createElement(node: ASTNode): PsiElement {
    if (node.elementType == FIXED_PREFIX_CONTENT) return FixedPrefixHost(node)
    return ASTWrapperPsiElement(node)
  }

  override fun createFile(viewProvider: FileViewProvider) = FixedPrefixFile(viewProvider)
}

private class FixedPrefixHost(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {
  override fun isValidHost() = true
  override fun updateText(text: String): PsiLanguageInjectionHost = throw UnsupportedOperationException()
  override fun createLiteralTextEscaper(): LiteralTextEscaper<FixedPrefixHost> = FixedPrefixEscaper(this)
}

private class FixedPrefixEscaper(host: FixedPrefixHost) : LiteralTextEscaper<FixedPrefixHost>(host) {
  override fun getRelevantTextRange(): TextRange =
    TextRange.create(PREFIX_LENGTH, myHost.textLength)

  override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int =
    offsetInDecoded + PREFIX_LENGTH

  override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
    outChars.append(rangeInsideHost.substring(myHost.text))
    return true
  }

  override fun isOneLine(): Boolean = true
}

private class FixedPrefixInjector : MultiHostInjector {
  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
    if (context !is FixedPrefixHost) return
    if (context.textLength <= PREFIX_LENGTH) return
    registrar.startInjecting(PlainTextLanguage.INSTANCE)
    registrar.addPlace(null, null, context, TextRange(PREFIX_LENGTH, context.textLength))
    registrar.doneInjecting()
  }

  override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(FixedPrefixHost::class.java)
}
