// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.propertyBased

import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.FileContentUtilCore
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.ref.GCUtil
import org.junit.Assert.fail

object PsiIndexConsistencyTester {
  val commonRefs: List<RefKind> = listOf(RefKind.PsiFileRef, RefKind.DocumentRef, RefKind.DirRef,
                                         RefKind.AstRef(null),
                                         RefKind.StubRef(null), RefKind.GreenStubRef(null))

  val commonActions: List<Action> = listOf(Action.Commit,
                                           Action.FlushIndexes,
                                           Action.Gc,
                                           Action.ReparseFile,
                                           Action.FilePropertiesChanged,
                                           Action.ReloadFromDisk,
                                           Action.Reformat,
                                           Action.PostponedFormatting,
                                           Action.RenamePsiFile,
                                           Action.RenameVirtualFile,
                                           Action.Save)

  fun refActions(refs: List<RefKind>): Iterable<Action> = refs.flatMap { listOf(Action.LoadRef(it), Action.ClearRef(it)) }

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

    object FlushIndexes: SimpleAction() {
      override fun performAction(model: Model) {
        (FileBasedIndex.getInstance() as FileBasedIndexImpl).flushIndexes()
      }
    }

    object Gc: SimpleAction() {
      override fun performAction(model: Model) = GCUtil.tryGcSoftlyReachableObjects()
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
        val oldValue = model.refs[kind]
        val newValue = kind.loadRef(model)
        if (oldValue !== null && newValue !== null && oldValue !== newValue) {
          kind.checkDuplicates(oldValue, newValue)
        }
        model.refs[kind] = newValue
      }
    }
    data class ClearRef(val kind: RefKind): Action {
      override fun performAction(model: Model) {
        model.refs.remove(kind)
      }
    }
    data class SetDocumentText(val text: String): Action {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        model.getDocument().setText(text)
      }
    }
    data class SetFileText(val text: String): Action {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        Save.performAction(model)
        VfsUtil.saveText(model.vFile, text)
      }
    }
  }

  abstract class RefKind {

    abstract fun loadRef(model: Model): Any?

    open fun checkDuplicates(oldValue: Any, newValue: Any) {
      if (oldValue is PsiElement && oldValue.isValid && newValue is PsiElement) {
        fail("Duplicate PSI elements: $oldValue and $newValue")
      }
    }

    object PsiFileRef : RefKind() {
      override fun loadRef(model: Model): Any = model.findPsiFile()
    }
    object DocumentRef : RefKind() {
      override fun loadRef(model: Model): Any = model.getDocument()
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