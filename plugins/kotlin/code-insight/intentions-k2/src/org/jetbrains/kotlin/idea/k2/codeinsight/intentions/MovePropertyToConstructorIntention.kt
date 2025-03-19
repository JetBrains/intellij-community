// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.isMovableToConstructorByPsi
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.moveToConstructor
import org.jetbrains.kotlin.psi.KtProperty

internal class MovePropertyToConstructorIntention
    : KotlinApplicableModCommandAction<KtProperty, MovePropertyToConstructorInfo>(KtProperty::class) {

    override fun getFamilyName(): String = KotlinBundle.message("move.to.constructor")

    override fun isApplicableByPsi(element: KtProperty): Boolean = element.isMovableToConstructorByPsi()

    override fun KaSession.prepareContext(element: KtProperty): MovePropertyToConstructorInfo? = MovePropertyToConstructorInfo.create(element)

    override fun invoke(
      actionContext: ActionContext,
      element: KtProperty,
      elementContext: MovePropertyToConstructorInfo,
      updater: ModPsiUpdater,
    ) {
        element.moveToConstructor(elementContext.toWritable(updater))
    }
}
