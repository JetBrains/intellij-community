/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework.propertyBased

import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.FileContentUtilCore
import org.jetbrains.jetCheck.Generator

/**
 * @author peter
 */
object PsiIndexConsistencyTester {

  val commonRefs: List<RefKind> = listOf(RefKind.PsiFileRef, RefKind.DocumentRef, RefKind.DirRef,
                                         RefKind.AstRef(null),
                                         RefKind.StubRef(null), RefKind.GreenStubRef(null))

  fun commonActions(refs: List<RefKind>): Generator<Action> = Generator.frequency(
    1, Generator.constant(Action.Gc),
    15, Generator.sampledFrom(Action.Commit,
                              Action.ReparseFile,
                              Action.FilePropertiesChanged,
                              Action.ReloadFromDisk,
                              Action.Reformat,
                              Action.PostponedFormatting,
                              Action.RenamePsiFile,
                              Action.RenameVirtualFile,
                              Action.Save),
    20, Generator.sampledFrom(refs).flatMap { Generator.sampledFrom(Action.LoadRef(it), Action.ClearRef(it)) })


  fun runActions(model: Model, vararg actions: Action) {
    WriteCommandAction.runWriteCommandAction(model.project) {
      try {
        actions.forEach { it.performAction(model) }
      }
      finally {
        try {
          Action.Save.performAction(model)
          model.vFile.delete(this)
        }
        catch (e: Throwable) {
          e.printStackTrace()
        }
      }
    }
  }


  open class Model(val vFile: VirtualFile, val fixture: CodeInsightTestFixture) {
    val refs = hashMapOf<RefKind, Any?>()
    val project = fixture.project!!

    fun findPsiFile(language: Language? = null) = findViewProvider().let { vp -> vp.getPsi(language ?: vp.baseLanguage) }!!
    private fun findViewProvider() = PsiManager.getInstance(project).findViewProvider(vFile)!!
    fun getDocument() = FileDocumentManager.getInstance().getDocument(vFile)!!
    fun isCommitted(): Boolean {
      val document = FileDocumentManager.getInstance().getCachedDocument(vFile)
      return document == null || PsiDocumentManager.getInstance(project).isCommitted(document)
    }

    open fun onCommit() {}
    open fun onReload() {}
    open fun onSave() {}
  }

  interface Action {

    fun performAction(model: Model)

    abstract class SimpleAction: Action {
      override fun toString(): String = javaClass.simpleName
    }

    object Gc: SimpleAction() {
      override fun performAction(model: Model) = PlatformTestUtil.tryGcSoftlyReachableObjects()
    }
    object Commit: SimpleAction() {
      override fun performAction(model: Model) {
        PsiDocumentManager.getInstance(model.project).commitAllDocuments()
        model.onCommit()
      }
    }
    object Save: SimpleAction() {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        FileDocumentManager.getInstance().saveAllDocuments()
        model.onSave()
      }
    }
    object PostponedFormatting: SimpleAction() {
      override fun performAction(model: Model) =
        PostprocessReformattingAspect.getInstance(model.project).doPostponedFormatting()
    }
    object ReparseFile : SimpleAction() {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        FileContentUtilCore.reparseFiles(model.vFile)
        model.onSave()
      }
    }
    object FilePropertiesChanged : SimpleAction() {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        PushedFilePropertiesUpdater.getInstance(model.project).filePropertiesChanged(model.vFile, Conditions.alwaysTrue())
      }
    }
    object ReloadFromDisk : SimpleAction() {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        PsiManager.getInstance(model.project).reloadFromDisk(model.findPsiFile())
        model.onReload()
        if (model.isCommitted()) {
          model.onCommit()
        }
      }
    }
    object RenameVirtualFile: SimpleAction() {
      override fun performAction(model: Model) {
        model.vFile.rename(this, model.vFile.nameWithoutExtension + "1." + model.vFile.extension)
      }
    }
    object RenamePsiFile: SimpleAction() {
      override fun performAction(model: Model) {
        val newName = model.vFile.nameWithoutExtension + "1." + model.vFile.extension
        model.findPsiFile().name = newName
        assert(model.findPsiFile().name == newName)
        assert(model.vFile.name == newName)
      }
    }
    object Reformat: SimpleAction() {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        Commit.performAction(model)
        CodeStyleManager.getInstance(model.project).reformat(model.findPsiFile())
      }
    }
    data class LoadRef(val kind: RefKind): Action {
      override fun performAction(model: Model) {
        model.refs[kind] = kind.loadRef(model)
      }
    }
    data class ClearRef(val kind: RefKind): Action {
      override fun performAction(model: Model) {
        model.refs.remove(kind)
      }
    }
  }

  abstract class RefKind {

    abstract fun loadRef(model: Model): Any?

    object PsiFileRef : RefKind() {
      override fun loadRef(model: Model): Any? = model.findPsiFile()
    }
    object DocumentRef : RefKind() {
      override fun loadRef(model: Model): Any? = model.getDocument()
    }
    object DirRef : RefKind() {
      override fun loadRef(model: Model): Any? = model.findPsiFile().containingDirectory
    }
    data class AstRef(val language: Language?) : RefKind() {
      override fun loadRef(model: Model): Any? = model.findPsiFile(language).node
    }
    data class StubRef(val language: Language?) : RefKind() {
      override fun loadRef(model: Model): Any? = (model.findPsiFile(language) as PsiFileImpl).stubTree
    }
    data class GreenStubRef(val language: Language?) : RefKind() {
      override fun loadRef(model: Model): Any? = (model.findPsiFile(language) as PsiFileImpl).greenStubTree
    }

  }
}