// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy
import com.intellij.psi.impl.compiled.ClsFieldImpl
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.impl.compiled.ClsMethodImpl
import com.intellij.psi.util.MethodSignatureUtil
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependencyAware

class ScriptDependencySourceNavigationPolicyForJavaClasses : ClsCustomNavigationPolicy {
    override fun getNavigationElement(clsClass: ClsClassImpl): PsiClass? {
        val containingClass = clsClass.containingClass as? ClsClassImpl
        if (containingClass != null) {
            return getNavigationElement(containingClass)?.findInnerClassByName(clsClass.name, false)
        }

        val clsFileImpl = clsClass.containingFile as? ClsFileImpl ?: return null
        return (getNavigationElement(clsFileImpl) as? PsiClassOwner)?.classes?.singleOrNull()
    }

    override fun getNavigationElement(clsMethod: ClsMethodImpl): PsiElement? {
        val clsClass = getNavigationElement(clsMethod.containingClass as ClsClassImpl) ?: return null
        return clsClass.findMethodsByName(clsMethod.name, false)
            .firstOrNull { MethodSignatureUtil.areParametersErasureEqual(it, clsMethod) }
    }

    override fun getNavigationElement(clsField: ClsFieldImpl): PsiElement? {
        val srcClass = getNavigationElement(clsField.containingClass as ClsClassImpl) ?: return null
        return srcClass.findFieldByName(clsField.name, false)
    }

    override fun getNavigationElement(file: ClsFileImpl): PsiElement? {
        val virtualFile = file.virtualFile
        val project = file.project
        val sourceFileName = (file.classes.firstOrNull() as? ClsClassImpl)?.sourceFileName ?: return null

        if (virtualFile !in ScriptDependencyAware.getInstance(project).getAllScriptsDependenciesClassFilesScope()) return null

        val packageName = file.packageName
        val relativePath = if (packageName.isEmpty()) sourceFileName else packageName.replace('.', '/') + '/' + sourceFileName

        for (root in ScriptDependencyAware.getInstance(project).getAllScriptDependenciesSources()) {
            if (!root.isValid) continue
            val sourceFile = root.findFileByRelativePath(relativePath)
            if (sourceFile != null && sourceFile.isValid) {
                val sourcePsi = file.manager.findFile(sourceFile)
                if (sourcePsi is PsiClassOwner) {
                    return sourcePsi
                }
            }
        }
        return null
    }
}