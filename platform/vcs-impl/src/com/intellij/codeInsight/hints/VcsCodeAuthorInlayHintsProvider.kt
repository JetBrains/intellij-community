// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.VcsCodeAuthorInlayHintsProvider.Companion.KEY
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil.disposeWithEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.actions.VcsAnnotateUtil
import com.intellij.openapi.vcs.annotate.AnnotationsPreloader
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vcs.annotate.LineAnnotationAspectAdapter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.ui.layout.*
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.CacheableAnnotationProvider
import javax.swing.JComponent

private fun isCodeAuthorEnabledForApplication(): Boolean =
  !application.isUnitTestMode && Registry.`is`("vcs.code.author.inlay.hints")

private fun isCodeAuthorEnabledInSettings(): Boolean =
  InlayHintsProviderExtension.findProviders()
    .filter { it.provider.key == KEY }
    .any { InlayHintsSettings.instance().hintsShouldBeShown(it.provider.key, it.language) }

private fun isCodeAuthorEnabledInSettings(language: Language): Boolean {
  val hasProviderForLanguage = InlayHintsProviderExtension.allForLanguage(language).any { it.key == KEY }
  return hasProviderForLanguage && InlayHintsSettings.instance().hintsShouldBeShown(KEY, language)
}

internal fun isCodeAuthorInlayHintsEnabled(): Boolean = isCodeAuthorEnabledForApplication() && isCodeAuthorEnabledInSettings()

@RequiresEdt
internal fun refreshCodeAuthorInlayHints(project: Project, file: VirtualFile) {
  if (!isCodeAuthorEnabledForApplication()) return

  val psiFile = PsiManagerEx.getInstanceEx(project).fileManager.getCachedPsiFile(file)
  if (psiFile != null && !isCodeAuthorEnabledInSettings(psiFile.language)) return

  val editors = VcsAnnotateUtil.getEditors(project, file)
  val noCodeAuthorEditors = editors.filter { it.getUserData(VCS_CODE_AUTHOR_ANNOTATION) == null }
  if (noCodeAuthorEditors.isEmpty()) return

  for (editor in noCodeAuthorEditors) InlayHintsPassFactory.clearModificationStamp(editor)
  if (psiFile != null) DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
}

abstract class VcsCodeAuthorInlayHintsProvider : InlayHintsProvider<NoSettings> {
  override val group: InlayGroup
    get() = InlayGroup.CODE_AUTHOR_GROUP

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
    if (!isCodeAuthorEnabledForApplication()) return null

    val authorAspect = getAspect(file, editor) ?: return null
    return VcsCodeAuthorInlayHintsCollector(editor, authorAspect, this::isAccepted, this::getClickHandler)
  }

  fun getAspect(file: PsiFile, editor: Editor): LineAnnotationAspect? {
    if (hasPreviewInfo(file)) return LineAnnotationAspectAdapter.NULL_ASPECT;
    val virtualFile = file.virtualFile ?: return null
    val annotation = getAnnotation(file.project, virtualFile, editor) ?: return null
    return annotation.aspects.find { it.id == LineAnnotationAspect.AUTHOR }
  }

  override fun getPlaceholdersCollectorFor(
    file: PsiFile,
    editor: Editor,
    settings: NoSettings,
    sink: InlayHintsSink
  ): InlayHintsCollector? {
    if (!isCodeAuthorEnabledForApplication()) return null
    if (!AnnotationsPreloader.isEnabled()) return null

    val virtualFile = file.virtualFile ?: return null
    if (!AnnotationsPreloader.canPreload(file.project, virtualFile)) return null

    return VcsCodeAuthorPlaceholdersCollector(editor, this::isAccepted)
  }

  protected abstract fun isAccepted(element: PsiElement): Boolean

  protected open fun getClickHandler(element: PsiElement): () -> Unit = {}

  override fun createSettings(): NoSettings = NoSettings()
  override val isVisibleInSettings: Boolean get() = isCodeAuthorEnabledForApplication()

  override val name: String get() = message("label.code.author.inlay.hints")
  override val key: SettingsKey<NoSettings> get() = KEY
  override val previewText: String? get() = null
  override fun getProperty(key: String): String? = message(key)

  override fun preparePreview(editor: Editor, file: PsiFile, settings: NoSettings) {
    addPreviewInfo(file)
  }

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
    object : ImmediateConfigurable {
      override fun createComponent(listener: ChangeListener): JComponent = panel {}
    }

  companion object {
    internal val KEY: SettingsKey<NoSettings> = SettingsKey("vcs.code.author")
  }
}

private val VCS_CODE_AUTHOR_ANNOTATION = Key.create<FileAnnotation>("Vcs.CodeAuthor.Annotation")

private fun getAnnotation(project: Project, file: VirtualFile, editor: Editor): FileAnnotation? {
  editor.getUserData(VCS_CODE_AUTHOR_ANNOTATION)?.let { return it }

  val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file) ?: return null
  val provider = vcs.annotationProvider as? CacheableAnnotationProvider ?: return null
  val annotation = provider.getFromCache(file) ?: return null

  val annotationDisposable = Disposable {
    unregisterAnnotation(file, annotation)
    annotation.dispose()
  }
  annotation.setCloser {
    editor.putUserData(VCS_CODE_AUTHOR_ANNOTATION, null)
    Disposer.dispose(annotationDisposable)

    project.service<AnnotationsPreloader>().schedulePreloading(file)
  }
  annotation.setReloader { annotation.close() }

  editor.putUserData(VCS_CODE_AUTHOR_ANNOTATION, annotation)
  registerAnnotation(file, annotation)
  disposeWithEditor(editor, annotationDisposable)

  return annotation
}

private fun registerAnnotation(file: VirtualFile, annotation: FileAnnotation) =
  ProjectLevelVcsManager.getInstance(annotation.project).annotationLocalChangesListener.registerAnnotation(file, annotation)

private fun unregisterAnnotation(file: VirtualFile, annotation: FileAnnotation) =
  ProjectLevelVcsManager.getInstance(annotation.project).annotationLocalChangesListener.unregisterAnnotation(file, annotation)
