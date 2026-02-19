package ru.adelf.idea.dotenv

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement
import ru.adelf.idea.dotenv.psi.DotEnvNestedVariableKey

class DotEnvNestedVariablesProvider : EnvironmentVariablesUsagesProvider {

    override fun acceptFile(file: VirtualFile): Boolean {
        return file.fileType == DotEnvFileType.INSTANCE
    }

    override fun getUsages(psiFile: PsiFile): Collection<KeyUsagePsiElement> {
        val collectedKeys: MutableSet<KeyUsagePsiElement> = mutableSetOf()
        psiFile.acceptChildren(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is DotEnvNestedVariableKey) {
                    collectedKeys.add(KeyUsagePsiElement(element.text, element))
                }
                super.visitElement(element)
            }
        })
        return collectedKeys
    }

}