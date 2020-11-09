package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.intentions.AddFullQualifierIntention
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument

class UseFullyQualifiedCallFix(
    val fqName: FqName,
    referenceExpression: KtNameReferenceExpression
) : KotlinQuickFixAction<KtNameReferenceExpression>(referenceExpression) {
    override fun getFamilyName() = KotlinBundle.message("fix.use.fully.qualified.call")
    override fun getText() = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val result = AddFullQualifierIntention.applyTo(element, fqName)
        ShortenReferences.DEFAULT.process(result)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): UseFullyQualifiedCallFix? {
            val warning = Errors.COMPATIBILITY_WARNING.cast(diagnostic)
            val element = warning.psiElement
            val descriptor = warning.a
            val fqName = descriptor.importableFqName ?: return null

            val referenceExpression = if (element is KtValueArgument) {
                val expression = element.getArgumentExpression() as? KtCallableReferenceExpression
                expression?.callableReference
            } else {
                element
            }

            val nameReferenceExpression = referenceExpression as? KtNameReferenceExpression ?: return null
            if (!AddFullQualifierIntention.isApplicableTo(nameReferenceExpression, descriptor)) return null
            return UseFullyQualifiedCallFix(fqName, nameReferenceExpression)
        }
    }
}
