// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform.java

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.java.codeserver.core.JavaPsiModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.impl.light.LightJavaModule
import org.jetbrains.kotlin.analysis.api.platform.java.KotlinJavaModuleAccessibilityChecker
import org.jetbrains.kotlin.analysis.api.platform.java.KotlinJavaModuleAccessibilityError
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.jvm.isJvm

internal class IdeKotlinJavaModuleAccessibilityChecker(private val project: Project) : KotlinJavaModuleAccessibilityChecker {
    override fun checkAccessibility(
        useSiteFile: VirtualFile?,
        referencedFile: VirtualFile,
        referencedPackage: FqName?
    ): KotlinJavaModuleAccessibilityError? {
        val ourModule = useSiteFile?.let(project::findJavaModule)?.also { if (it is LightJavaModule) return null }
        val theirModule = findJvmModule(referencedFile)

        if (ourModule?.name == theirModule?.name) return null

        if (theirModule == null) {
            return KotlinJavaModuleAccessibilityError.ModuleDoesNotReadUnnamedModule
        }

        if (ourModule != null && !JavaPsiModuleUtil.reads(ourModule, theirModule)) {
            return KotlinJavaModuleAccessibilityError.ModuleDoesNotReadModule(theirModule.name)
        }

        // In the IDE, we allow unnamed module to access unexported package of the named module. The reason is that the compiler
        // will use classpath, not the module path, when compilation of this module is launched from the IDE (because the module has
        // no module-info). All of its dependencies will also land on the classpath, and everything is visible in the classpath,
        // even non-exported packages of artifacts which would otherwise be loaded as named modules, if they were on the module path.
        // So, no error will be reported from the compiler. Moreover, a run configuration of something from this unnamed module will also
        // use classpath, not the module path, and in the same way everything will work at runtime as well.
        if (ourModule != null) {
            val fqName = referencedPackage?.asString() ?: return null
            if (theirModule.name != PsiJavaModule.JAVA_BASE && !exports(theirModule, fqName, ourModule)) {
                return KotlinJavaModuleAccessibilityError.ModuleDoesNotExportPackage(theirModule.name)
            }
        }

        return null
    }

    private fun findJvmModule(referencedFile: VirtualFile): PsiJavaModule? {
        val referencedModuleForJvm = project.findJavaModule(referencedFile)
        if (referencedModuleForJvm != null) return referencedModuleForJvm

        val index = ProjectFileIndex.getInstance(project)
        if (index.isInLibrary(referencedFile)) return null

        val referencedModule = index.getModuleForFile(referencedFile) ?: return null
        val implementingModules = referencedModule.implementingModules
        val jvmModule = implementingModules.find { it.platform.isJvm() } ?: return null

        val inTestSourceContent = index.isInTestSourceContent(referencedFile)

        val jvmModuleDescriptor = JavaModuleGraphUtil.findDescriptorByModule(jvmModule, inTestSourceContent)
        if (jvmModuleDescriptor != null) return jvmModuleDescriptor

        val implementedJvmModule = jvmModule.implementedModules.find { it.platform.isJvm() }
        return JavaModuleGraphUtil.findDescriptorByModule(implementedJvmModule, inTestSourceContent)
    }

    /**
     * Whether [source] exports [packageName] to [target].
     */
    private fun exports(source: PsiJavaModule, packageName: String, target: PsiJavaModule): Boolean =
      source is LightJavaModule || JavaPsiModuleUtil.exports(source, packageName, target)
}
