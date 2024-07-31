// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass

class AddJvmInlineAnnotationFix(klass: KtClass) : PsiUpdateModCommandAction<KtClass>(klass) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtClass,
        updater: ModPsiUpdater,
    ) {
        val jvmInlineFqName = FqName("kotlin.jvm.JvmInline")
        val jvmInlineClassId = ClassId.topLevel(jvmInlineFqName)
        element.addAnnotation(jvmInlineClassId)
    }

    override fun getFamilyName(): String = KotlinBundle.message("add.jvminline.annotation")
}
