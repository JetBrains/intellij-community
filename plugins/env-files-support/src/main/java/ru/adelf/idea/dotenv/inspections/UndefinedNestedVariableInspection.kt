package ru.adelf.idea.dotenv.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import ru.adelf.idea.dotenv.DotEnvBundle
import ru.adelf.idea.dotenv.indexing.DotEnvKeyValuesIndex
import ru.adelf.idea.dotenv.psi.DotEnvNestedVariableKey

class UndefinedNestedVariableInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                (element as? DotEnvNestedVariableKey)?.let { key ->
                    val isUndefinedProperty = FileBasedIndex.getInstance().processValues(
                        DotEnvKeyValuesIndex.KEY,
                        element.text, null,
                        FileBasedIndex.ValueProcessor { file: VirtualFile?, value: String? -> false },
                        GlobalSearchScope.allScope(element.project)
                    )
                    if (isUndefinedProperty) {
                        holder.registerProblem(element, DotEnvBundle.message("inspection.name.undefined.nested.variable", element.text))
                    }
                }
                super.visitElement(element)
            }
        }
    }

}