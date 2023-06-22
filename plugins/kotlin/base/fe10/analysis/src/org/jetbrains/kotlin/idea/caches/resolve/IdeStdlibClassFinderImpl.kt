// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.caches.resolve.util.javaResolutionFacade
import org.jetbrains.kotlin.idea.resolve.ideService
import org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
import org.jetbrains.kotlin.load.java.structure.impl.source.JavaElementSourceFactory
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.resolve.StdlibClassFinder
import org.jetbrains.kotlin.resolve.jvm.JavaDescriptorResolver

internal class IdeStdlibClassFinderImpl(
  private val project: Project,
) : StdlibClassFinder {
    override fun findEnumEntriesClass(moduleDescriptor: ModuleDescriptor): ClassDescriptor? {
        moduleDescriptor.findClassAcrossModuleDependencies(StandardClassIds.EnumEntries)?.let { return it }

        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val libScope = ProjectScope.getLibrariesScope(javaPsiFacade.project)
        val psiClasses = javaPsiFacade.findClasses(StandardClassIds.EnumEntries.asFqNameString(), libScope)
        if (psiClasses.isEmpty()) return null
        val sourceFactory = JavaElementSourceFactory.getInstance(project)
        return psiClasses.asSequence()
            // Filter out class not belonging to module which comes from embedded kotlin-stdlib.jar added for .kts support
            .filter { it.moduleInfoOrNull != null }
            .map { psiClass ->
                val psiJavaSource = sourceFactory.createPsiSource(psiClass)
                psiClass.javaResolutionFacade()?.ideService<JavaDescriptorResolver>()?.resolveClass(JavaClassImpl(psiJavaSource))
            }
            .firstOrNull()
    }
}
