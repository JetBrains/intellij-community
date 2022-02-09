// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacadeImpl
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.caches.project.getModuleInfo
import org.jetbrains.kotlin.idea.caches.resolve.util.isInDumbMode
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFileClassProvider
import org.jetbrains.kotlin.psi.analysisContext
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.scripting.definitions.runReadAction

class KtFileClassProviderImpl(val project: Project) : KtFileClassProvider {

    override fun getFileClasses(file: KtFile): Array<PsiClass> {
        if (file.project.isInDumbMode()) {
            return PsiClass.EMPTY_ARRAY
        }

        // TODO We don't currently support finding light classes for scripts
        if (file.isCompiled || runReadAction { file.isScript() }) {
            return PsiClass.EMPTY_ARRAY
        }

        val moduleInfo = file.getModuleInfo()
        // prohibit obtaining light classes for non-jvm modules trough KtFiles
        // common files might be in fact compiled to jvm and thus correspond to a PsiClass
        // this API does not provide context (like GSS) to be able to determine if this file is in fact seen through a jvm module
        // this also fixes a problem where a Java JUnit run configuration producer would produce run configurations for a common file
        if (!moduleInfo.platform.isJvm()) return emptyArray()

        val result = arrayListOf<PsiClass>()
        file.declarations.filterIsInstance<KtClassOrObject>().mapNotNullTo(result) { it.toLightClass() }

        val jvmClassInfo = JvmFileClassUtil.getFileClassInfoNoResolve(file)
        if (!jvmClassInfo.withJvmMultifileClass && !file.hasTopLevelCallables()) return result.toTypedArray()

        val kotlinAsJavaSupport = KotlinAsJavaSupport.getInstance(project)
        if (file.analysisContext == null) {
            kotlinAsJavaSupport
                .getFacadeClasses(file.javaFileFacadeFqName, moduleInfo.contentScope())
                .filterTo(result) { it is KtLightClassForFacade && file in it.files }
        } else {
            result.add(kotlinAsJavaSupport.createFacadeForSyntheticFile(file.javaFileFacadeFqName, file))
        }

        return result.toTypedArray()
    }
}
