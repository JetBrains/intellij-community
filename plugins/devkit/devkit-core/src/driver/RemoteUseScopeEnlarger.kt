package org.jetbrains.idea.devkit.driver

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope.allScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger

const val REMOTE_ANNOTATION_FQN = "com.intellij.driver.client.Remote"

internal class RemoteUseScopeEnlarger : UseScopeEnlarger() {
    override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
        if (element !is PsiClass && element !is PsiMethod) return null

        if (element is PsiMethod
            && (element.hasModifier(JvmModifier.PRIVATE)
                    || element.hasModifier(JvmModifier.PACKAGE_LOCAL)
                    || element.hasModifier(JvmModifier.PROTECTED))
        ) {
            return null
        }

        val project = element.project
        if (!JavaLibraryUtil.hasLibraryClass(project, REMOTE_ANNOTATION_FQN)) return null

        val remoteClass = JavaPsiFacade.getInstance(project)
            .findClass(REMOTE_ANNOTATION_FQN, allScope(project)) ?: return null

        return remoteClass.useScope
    }
}