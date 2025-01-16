// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.onboarding

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.tree.IElementType
import java.util.function.Consumer


abstract class AbstractOnboardingTipsDocumentationProvider(private val commentTokenType: IElementType) : DocumentationProvider {
  protected open val tipPrefix: String = "//TIP"
  protected val enabled get() = renderedOnboardingTipsEnabled

  protected abstract fun isLanguageFile(file: PsiFile): Boolean

  private fun isEnabledForFile(file: PsiFile): Boolean {
    return enabled && isLanguageFile(file)
  }

  override fun collectDocComments(file: PsiFile, sink: Consumer<in PsiDocCommentBase>) {
    if (!isEnabledForFile(file)) return

    val filePath = file.virtualFile?.path ?: return
    val onboardingTipsDebugPath = file.project.filePathWithOnboardingTips
    if (filePath != onboardingTipsDebugPath) {
      return
    }

    val visitedComments = mutableSetOf<PsiElement>()

    file.accept(object : PsiRecursiveElementVisitor() {
      override fun visitComment(comment: PsiComment) {
        if (visitedComments.contains(comment)) return
        if (comment.node.elementType != commentTokenType) return

        if (comment.text.startsWith(tipPrefix)) {
          val wrapper = createOnboardingTipComment(comment, visitedComments, commentTokenType)
          sink.accept(wrapper)
        }
      }
    })
  }

  override fun findDocComment(file: PsiFile, range: TextRange): PsiDocCommentBase? {
    if (isEnabledForFile(file)) return null
    var result: PsiDocCommentBase? = null
    file.accept(object: PsiRecursiveElementVisitor() {
      override fun visitComment(comment: PsiComment) {
        if (comment.textRange.startOffset != range.startOffset) return
        result = OnboardingTipComment(comment.parent, range, commentTokenType)
      }
    })

    return result
  }

  override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
    if (!enabled || comment !is OnboardingTipComment) return null
    val result = comment.text
      .split("\n")
      .map { it.trim() }
      .map { if (it.startsWith(tipPrefix)) it.substring(tipPrefix.length, it.length) else it }
      .map { if (it.startsWith("//")) it.substring(2, it.length) else it }
      .joinToString(separator = " ").trim()
    @Suppress("HardCodedStringLiteral")
    return "<p>$result"
  }
}

private fun createOnboardingTipComment(start: PsiComment, visitedComments: MutableSet<PsiElement>, commentTokenType: IElementType): OnboardingTipComment {
  var current: PsiElement = start
  while(true) {
    var nextSibling = current.nextSibling
    while (nextSibling is PsiWhiteSpace) nextSibling = nextSibling.nextSibling
    if (nextSibling?.node?.elementType != commentTokenType) break
    visitedComments.add(nextSibling)
    current = nextSibling
  }
  return OnboardingTipComment(current.parent, TextRange(start.textRange.startOffset, current.textRange.endOffset), commentTokenType)
}

private class OnboardingTipComment(
  private val parent: PsiElement,
  private val range: TextRange,
  private val commentTokenType: IElementType
): FakePsiElement(), PsiDocCommentBase {
  override fun getParent() = parent

  override fun getTokenType(): IElementType = commentTokenType

  override fun getTextRange() = range

  override fun getText() = range.substring(parent.containingFile.text)

  override fun getOwner() = parent
}