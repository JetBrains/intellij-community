package ru.adelf.idea.dotenv.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import ru.adelf.idea.dotenv.DotEnvBundle
import ru.adelf.idea.dotenv.psi.DotEnvValue

class NestedVariableOutsideDoubleQuotesInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                (element as? DotEnvValue)?.let {
                    processValue(it, holder)
                }
                super.visitElement(element)
            }
        }
    }

    private fun processValue(value: DotEnvValue, holder: ProblemsHolder) {
        findUnquotedRange(value)?.let {
            holder.registerProblem(
                value,
                DotEnvBundle.message("inspection.name.nested.variable.outside.double.quotes"),
                ProblemHighlightType.ERROR,
                TextRange(
                    it.startOffset,
                    it.endOffset
                ),
                QuoteEnvironmentVariableValueQuickFix()
            )

        }
    }

    private class QuoteEnvironmentVariableValueQuickFix() : LocalQuickFix {

        override fun getFamilyName(): @IntentionFamilyName String {
            return DotEnvBundle.message("quickfix.name.put.environment.variable.value.inside.quotes")
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            (descriptor.psiElement as? DotEnvValue)?.let { element ->
                findUnquotedRange(element)?.let { range ->
                    element.containingFile.fileDocument.replaceString(
                        element.textRange.startOffset + range.startOffset,
                        element.textRange.startOffset + range.endOffset,
                        "\"${StringUtil.trim(element.text)}\""
                    )
                }
            }
        }

    }

}

private fun findUnquotedRange(value: DotEnvValue): TextRange? {
    val text = value.text
    if (!text.contains("\${")) {
        return null
    }
    val leftOffset = StringUtil.skipWhitespaceForward(text, 0)
    val rightOffset = StringUtil.skipWhitespaceBackward(text, text.length)
    if (text[leftOffset] == '"' && text[rightOffset - 1] == '"') {
        return null
    }
    return TextRange(leftOffset, rightOffset)
}
