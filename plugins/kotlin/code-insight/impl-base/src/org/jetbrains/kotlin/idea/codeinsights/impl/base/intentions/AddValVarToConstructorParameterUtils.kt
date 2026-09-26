// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.psi.mustHaveOnlyValPropertiesInPrimaryConstructor
import org.jetbrains.kotlin.idea.codeinsight.utils.ValVarExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClass

fun addValVarToConstructorParameter(
    project: Project,
    element: KtParameter,
    updater: ModPsiUpdater,
) {
    val valKeyword = element.addBefore(KtPsiFactory(project).createValKeyword(), element.nameIdentifier)
    if (element.containingClass()?.mustHaveOnlyValPropertiesInPrimaryConstructor() == true) return
    updater.templateBuilder().field(valKeyword, ValVarExpression)
}
