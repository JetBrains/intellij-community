// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.analysis.decompiled.light.classes.DecompiledLightClassesFactory
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.fileClasses.isJvmMultifileClassFile
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.base.util.isInDumbMode
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFileClassProvider
import org.jetbrains.kotlin.psi.analysisContext
import org.jetbrains.kotlin.scripting.definitions.runReadAction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KtFileClassProviderImpl(val project: Project) : KtFileClassProvider {
    override fun getFileClasses(file: KtFile): Array<PsiClass> {
        if (file.project.isInDumbMode) return emptyArray()

        if (file.isCompiled) {
            return file.safeAs<KtClsFile>()?.let {
                DecompiledLightClassesFactory.createLightClassForDecompiledKotlinFile(it, project)
            }?.let { arrayOf(it) } ?: emptyArray()
        }
        // TODO We don't currently support finding light classes for scripts
        if (runReadAction { file.isScript() }) return emptyArray()

        @OptIn(K1ModeProjectStructureApi::class)
        val moduleInfo = file.moduleInfoOrNull ?: return emptyArray()

        // prohibit obtaining light classes for non-jvm modules trough KtFiles
        // common files might be in fact compiled to jvm and thus correspond to a PsiClass
        // this API does not provide context (like GSS) to be able to determine if this file is in fact seen through a jvm module
        // this also fixes a problem where a Java JUnit run configuration producer would produce run configurations for a common file
        @OptIn(K1ModeProjectStructureApi::class)
        if (!moduleInfo.platform.isJvm()) return emptyArray()

        val result = arrayListOf<PsiClass>()
        file.declarations.filterIsInstance<KtClassOrObject>().mapNotNullTo(result) { it.toLightClass() }

        if (!file.isJvmMultifileClassFile && !file.hasTopLevelCallables()) return result.toTypedArray()

        val kotlinAsJavaSupport = KotlinAsJavaSupport.getInstance(project)
        if (file.analysisContext == null) {
            kotlinAsJavaSupport.getLightFacade(file)?.let(result::add)
        } else {
            result.add(kotlinAsJavaSupport.createFacadeForSyntheticFile(file))
        }

        return result.toTypedArray()
    }
}
