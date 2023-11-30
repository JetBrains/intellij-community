// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.java.analysis.OuterModelsModificationTrackerManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.isGetEntriesMethod
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.codeInsight.isEnumValuesSoftDeprecateEnabled
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

internal class EnumValuesSoftDeprecateInJavaInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                if ("values" != expression.methodExpression.referenceName
                    || !expression.argumentList.isEmpty
                ) {
                    return
                }

                val module = ModuleUtilCore.findModuleForFile(holder.file)
                if (module == null
                    || !hasKotlinFiles(module)
                    || !holder.file.isEnumValuesSoftDeprecateEnabled()
                ) {
                    return
                }

                val resolvedMethod = expression.resolveMethod()
                val containingClass = (resolvedMethod as? KtLightMethod)?.containingClass
                if (containingClass?.isEnum == true &&
                    containingClass.methods.any { isGetEntriesMethod(it) }
                ) {
                    holder.registerProblem(
                        expression,
                        KotlinBundle.message("inspection.enum.values.method.soft.deprecate.in.java.display.name"),
                    )
                }
            }
        }
    }
}

internal fun hasKotlinFiles(module: Module): Boolean {
    return CachedValuesManager.getManager(module.project).getCachedValue(module, CachedValueProvider {
        val hasKotlinFiles = FileTypeIndex.containsFileOfType(
            KotlinFileType.INSTANCE,
            moduleWithDependenciesAndLibrariesScope(module)
        )
        Result.create(hasKotlinFiles, OuterModelsModificationTrackerManager.getTracker(module.project))
    })
}