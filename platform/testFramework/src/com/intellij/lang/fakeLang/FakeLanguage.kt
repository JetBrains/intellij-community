// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.fakeLang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.*
import com.intellij.lexer.EmptyLexer
import com.intellij.lexer.Lexer
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import javax.swing.Icon

fun registerFakeLanguage(testRootDisposable: Disposable) {
  (FileTypeManager.getInstance() as FileTypeManagerImpl).registerFileType(
    /* type = */ FakeFileType.INSTANCE,
    /* defaultAssociations = */ listOf(ExtensionFileNameMatcher(FakeFileType.INSTANCE.defaultExtension)),
    /* disposable = */ testRootDisposable,
    /* pluginDescriptor = */ PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!
  )

  LanguageParserDefinitions.INSTANCE.addExplicitExtension(
    /* key = */ FakeLanguage,
    /* t = */ FakeParserDefinition(),
    /* parentDisposable = */ testRootDisposable
  )
}

object FakeLanguage : Language("FakeLang")

private class FakeParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = EmptyLexer()
  override fun createParser(project: Project?): PsiParser = FakeParser()
  override fun getFileNodeType(): IFileElementType = FakeFileElementType
  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY
  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
  override fun createElement(node: ASTNode?): PsiElement = throw UnsupportedOperationException()
  override fun createFile(viewProvider: FileViewProvider): PsiFile = FakeFile(viewProvider)
}

private class FakeParser : PsiParser {
  override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
    val marker = builder.mark()
    builder.advanceLexer()
    marker.done(root)
    return builder.treeBuilt
  }
}

private class FakeFileType : LanguageFileType(FakeLanguage) {
  override fun getName(): String = "FakeFileType"
  override fun getDescription(): String = "Fake file type"
  override fun getDefaultExtension(): String = "fake"
  override fun getIcon(): Icon? = null

  companion object {
    @JvmField
    val INSTANCE = FakeFileType()
  }
}

class FakeFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, FakeLanguage), PsiExternalReferenceHost {
  override fun getFileType(): FileType = FakeFileType.INSTANCE
}

private object FakeFileElementType : IFileElementType("FakeFileElementType", FakeLanguage)