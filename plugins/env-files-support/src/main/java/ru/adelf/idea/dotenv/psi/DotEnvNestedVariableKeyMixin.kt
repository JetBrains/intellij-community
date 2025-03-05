package ru.adelf.idea.dotenv.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner

abstract class DotEnvNestedVariableKeyMixin(node: ASTNode): ASTWrapperPsiElement(node), PsiNameIdentifierOwner {

    override fun getName(): @NlsSafe String? = text

    override fun setName(name: @NlsSafe String): PsiElement? = DotEnvElementFactory
        .createNestedVariableKey(project, name)
        .let(::replace)

    override fun getNameIdentifier(): PsiElement? = this

}