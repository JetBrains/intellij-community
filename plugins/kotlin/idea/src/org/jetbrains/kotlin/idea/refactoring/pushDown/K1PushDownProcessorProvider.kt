// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.pushDown

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.psi.KtClass

internal class K1PushDownProcessorProvider : KotlinPushDownProcessorProvider {
    override fun createPushDownProcessor(
        project: Project,
        sourceClass: KtClass,
        membersToMove: List<KotlinMemberInfo>,
    ): KotlinPushDownProcessor = K1PushDownProcessor(project, sourceClass, membersToMove)
}
