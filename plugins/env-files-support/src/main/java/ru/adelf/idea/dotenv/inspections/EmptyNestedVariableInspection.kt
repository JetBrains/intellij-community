package ru.adelf.idea.dotenv.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.elementType
import ru.adelf.idea.dotenv.DotEnvBundle
import ru.adelf.idea.dotenv.psi.DotEnvTypes.NESTED_VARIABLE_END
import ru.adelf.idea.dotenv.psi.DotEnvTypes.NESTED_VARIABLE_START

class EmptyNestedVariableInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.elementType == NESTED_VARIABLE_START && element.nextSibling?.elementType == NESTED_VARIABLE_END) {
                    holder.registerProblem(
                        element,
                        DotEnvBundle.message("inspection.name.empty.nested.variable"),
                    )
                }
                super.visitElement(element)
            }
        }
    }

}