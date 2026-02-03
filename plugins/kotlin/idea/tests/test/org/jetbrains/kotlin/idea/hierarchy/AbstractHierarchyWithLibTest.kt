// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.hierarchy

import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import com.intellij.ide.hierarchy.type.TypeHierarchyTreeStructure
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.psi.KtClassOrObject

abstract class AbstractHierarchyWithLibTest : AbstractHierarchyTest() {
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
                    is PsiClass -> TypeHierarchyTreeStructure(
                        project,
                        targetClass,
                        HierarchyBrowserBaseEx.SCOPE_PROJECT
                    )
                    is KtClassOrObject -> {
                        val lightClass = targetClass.toLightClass() ?: error("Can't get light class for $targetClass")
                        TypeHierarchyTreeStructure(
                            project,
                            lightClass,
                            HierarchyBrowserBaseEx.SCOPE_PROJECT
                        )
                    }
                    else -> error("Could not find java class or kotlin class: $directive")
                }
            }, *filesToConfigure
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()
}