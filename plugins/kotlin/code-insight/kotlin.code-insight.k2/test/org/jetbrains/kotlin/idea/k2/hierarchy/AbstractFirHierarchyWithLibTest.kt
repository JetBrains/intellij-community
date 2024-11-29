// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hierarchy

import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import com.intellij.ide.hierarchy.type.TypeHierarchyTreeStructure
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.types.KotlinTypeHierarchyTreeStructure
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.psi.KtClassOrObject

abstract class AbstractFirHierarchyWithLibTest : AbstractFirHierarchyTest() {
    protected fun doTest(folderName: String) {
        this.folderName = folderName

        val filesToConfigure = filesToConfigure
        val file = filesToConfigure.first()
        val directive = InTextDirectivesUtils.findLinesWithPrefixesRemoved(
            KotlinRoot.DIR.resolve("$folderName/$file").readText(),
            "// BASE_CLASS: "
        ).singleOrNull() ?: error("File should contain BASE_CLASS directive")

        doHierarchyTest(
            Computable {
                val targetClass = findTargetLibraryClass(directive.trim())

                when (targetClass) {
                    is PsiClass ->
                        TypeHierarchyTreeStructure(
                            project,
                            targetClass,
                            HierarchyBrowserBaseEx.SCOPE_PROJECT
                        )
                    is KtClassOrObject ->
                        KotlinTypeHierarchyTreeStructure(
                            project,
                            targetClass,
                            HierarchyBrowserBaseEx.SCOPE_PROJECT
                        )
                    else -> error("Could not find java class or kotlin class: $directive")
                }
            }, *filesToConfigure
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()
}