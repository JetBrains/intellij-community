// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.overrideImplement.*
import org.jetbrains.kotlin.idea.core.util.KotlinIdeaCoreBundle
import org.jetbrains.kotlin.psi.KtClassOrObject

internal class KtDelegateJavaDefaultMethodsQuickFix(
    private val members: Collection<KtClassMemberInfo>,
    private val bodyType: BodyType,
) : KtImplementMembersHandler(), IntentionAction {

    override fun getFamilyName(): String = when (bodyType) {
        is BodyType.Delegate -> KotlinBundle.message("override.java.default.methods.delegate.fix.text")
        is BodyType.Super -> KotlinBundle.message("override.java.default.methods.superclass.fix.text")
        else -> {
            Logger.getInstance(javaClass).error("Unexpected body type: $bodyType")
            ""
        }
    }

    override fun getText(): String = familyName
    override fun startInWriteAction(): Boolean = false
    override fun getChooserTitle(): String = KotlinIdeaCoreBundle.message("override.members.handler.title")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = when (bodyType) {
        is BodyType.Delegate, BodyType.Super -> true
        else -> false
    }

    override fun collectMembersToGenerate(classOrObject: KtClassOrObject): Collection<KtClassMember> = members.map {
        createKtClassMember(
            memberInfo = it,
            bodyType = bodyType,
            preferConstructorParameter = false,
        )
    }
}
