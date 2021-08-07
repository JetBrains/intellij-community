// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.modules

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.impl.light.LightJavaModule
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleResolver

class IdeJavaModuleResolver(private val project: Project) : JavaModuleResolver {
    private fun findJavaModule(file: VirtualFile): PsiJavaModule? = JavaModuleGraphUtil.findDescriptorByFile(file, project)

    override fun checkAccessibility(
        fileFromOurModule: VirtualFile?, referencedFile: VirtualFile, referencedPackage: FqName?
    ): JavaModuleResolver.AccessError? {
        val ourModule = fileFromOurModule?.let(this::findJavaModule)
        val theirModule = findJavaModule(referencedFile)

        if (ourModule?.name == theirModule?.name) return null

        if (theirModule == null) {
            return JavaModuleResolver.AccessError.ModuleDoesNotReadUnnamedModule
        }

        if (ourModule != null && !JavaModuleGraphUtil.reads(ourModule, theirModule)) {
            return JavaModuleResolver.AccessError.ModuleDoesNotReadModule(theirModule.name)
        }

        // In the IDE, we allow unnamed module to access unexported package of the named module. The reason is that the compiler
        // will use classpath, not the module path, when compilation of this module is launched from the IDE (because the module has
        // no module-info). All of its dependencies will also land on the classpath, and everything is visible in the classpath,
        // even non-exported packages of artifacts which would otherwise be loaded as named modules, if they were on the module path.
        // So, no error will be reported from the compiler. Moreover, a run configuration of something from this unnamed module will also
        // use classpath, not the module path, and in the same way everything will work at runtime as well.
        if (ourModule != null) {
            val fqName = referencedPackage?.asString() ?: return null
            if (!exports(theirModule, fqName, ourModule)) {
                return JavaModuleResolver.AccessError.ModuleDoesNotExportPackage(theirModule.name)
            }
        }

        return null
    }

    // Returns whether or not [source] exports [packageName] to [target]
    private fun exports(source: PsiJavaModule, packageName: String, target: PsiJavaModule): Boolean =
        source is LightJavaModule || JavaModuleGraphUtil.exports(source, packageName, target)
}
