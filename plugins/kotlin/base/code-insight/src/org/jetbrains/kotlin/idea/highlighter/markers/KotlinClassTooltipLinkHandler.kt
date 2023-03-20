// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.codeInsight.highlighting.TooltipLinkHandler
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex

class KotlinClassTooltipLinkHandler : TooltipLinkHandler() {
    override fun handleLink(refSuffix: String, editor: Editor): Boolean {
        val project = editor.project ?: return false
        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val qualifiedName = refSuffix.substringAfterLast(":")
        val scope = if (qualifiedName.length == refSuffix.length) {
            GlobalSearchScope.allScope(project)
        } else {
            val moduleName = refSuffix.substringBeforeLast(":")
            val module = ModuleManager.getInstance(project).findModuleByName(moduleName)
            module?.let { GlobalSearchScope.moduleScope(it) } ?: GlobalSearchScope.allScope(project)
        }
        // Non-JVM classes cannot be found with Java PSI Facade
        val aClassElement = KotlinFullClassNameIndex.get(qualifiedName, project, scope).firstOrNull()
            ?: javaPsiFacade.findClass(qualifiedName, scope)
            ?: return false
        NavigationUtil.activateFileWithPsiElement(aClassElement)
        return true
    }
}