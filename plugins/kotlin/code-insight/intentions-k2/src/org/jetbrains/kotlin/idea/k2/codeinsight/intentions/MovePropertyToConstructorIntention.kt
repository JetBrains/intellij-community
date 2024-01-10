// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.isMovableToConstructorByPsi
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.MovePropertyToConstructorUtils.moveToConstructor
import org.jetbrains.kotlin.psi.KtProperty

internal class MovePropertyToConstructorIntention
    : AbstractKotlinModCommandWithContext<KtProperty, MovePropertyToConstructorInfo>(KtProperty::class) {

    override fun getFamilyName(): String = KotlinBundle.message("move.to.constructor")

    override fun getActionName(element: KtProperty, context: MovePropertyToConstructorInfo): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtProperty> =
        ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtProperty): Boolean =
        element.isMovableToConstructorByPsi()

    context(KtAnalysisSession)
    override fun prepareContext(element: KtProperty): MovePropertyToConstructorInfo? =
        MovePropertyToConstructorInfo.create(element)

    override fun apply(element: KtProperty, context: AnalysisActionContext<MovePropertyToConstructorInfo>, updater: ModPsiUpdater) {
        element.moveToConstructor(context.analyzeContext.toWritable(updater))
    }
}
