// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.lang.java.JavaDocumentationTarget
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.ProjectIconsAccessor
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UField
import org.jetbrains.uast.toUElementOfType

/**
 * Shows the icon of an icon field in the documentation popup.
 * The icon comes from the field initializer, the same as in [com.intellij.codeInsight.daemon.impl.IconLineMarkerProvider].
 */
internal class IconFieldDocumentationTargetProvider : PsiDocumentationTargetProvider {
  override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
    if (element !is PsiField || resolveFieldIcon(element) == null) return null
    return IconFieldDocumentationTarget(element, originalElement)
  }
}

/**
 * Adds the icon preview to the Java documentation of [field].
 * All other parts come from [JavaDocumentationTarget].
 */
private class IconFieldDocumentationTarget(
  private val field: PsiField,
  private val originalElement: PsiElement?,
) : DocumentationTarget {
  private val javaTarget = JavaDocumentationTarget(field, originalElement)

  override fun createPointer(): Pointer<out DocumentationTarget> {
    val fieldPointer = field.createSmartPointer()
    val originalElementPointer = originalElement?.createSmartPointer()
    return Pointer {
      val field = fieldPointer.dereference() ?: return@Pointer null
      IconFieldDocumentationTarget(field, originalElementPointer?.dereference())
    }
  }

  override fun computePresentation(): TargetPresentation = javaTarget.computePresentation()

  override val navigatable: Navigatable?
    get() = javaTarget.navigatable

  override fun computeDocumentationHint(): String? = javaTarget.computeDocumentationHint()

  override fun computeDocumentation(): DocumentationResult? {
    val urls = JavaDocumentationProvider.getExternalJavaDocUrl(field)
    val html = JavaDocumentationProvider.generateExternalJavadoc(field, urls) ?: return null
    val documentation = DocumentationResult.documentation(html)

    val images = resolveFieldIcon(field) ?: return documentation
    val url = VfsUtilCore.convertToURL(images.themedFile.url)?.toString() ?: return documentation
    val topElement = DocumentationMarkup.TOP_ELEMENT
    val preview = topElement.children(
      topElement.child(HtmlChunk.tag("b").addText(DevKitBundle.message("icon.label.quickdoc", images.file.name))),
      topElement.child(HtmlChunk.tag("img").attr("src", url))
    ).toString()
    return documentation
      .html(preview + html)
  }
}

private data class IconFiles(val file: VirtualFile, val themedFile: VirtualFile)

private fun resolveFieldIcon(field: PsiField): IconFiles? {
  if (!ProjectIconsAccessor.isIconClassType(field.type)) return null

  val sourceField = field.navigationElement as? PsiField ?: return null
  val initializer = sourceField.toUElementOfType<UField>()?.uastInitializer ?: return null

  val iconsAccessor = ProjectIconsAccessor.getInstance(field.project)
  val iconFile = iconsAccessor.resolveIconFile(initializer) ?: return null

  return IconFiles(iconFile, ProjectIconsAccessor.resolveIconFile(iconFile))
}
