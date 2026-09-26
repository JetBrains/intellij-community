// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateConstructorRequest
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.computeExpectedParams
import org.jetbrains.kotlin.psi.KtCallElement

internal class CreateConstructorFromKotlinUsageRequest(
    call: KtCallElement,
    modifiers: Collection<JvmModifier>,
) : CreateExecutableFromKotlinUsageRequest<KtCallElement>(
    call = call,
    expectedParameterInfo = analyze(call) { computeExpectedParams(call) },
    modifiers = modifiers,
), CreateConstructorRequest