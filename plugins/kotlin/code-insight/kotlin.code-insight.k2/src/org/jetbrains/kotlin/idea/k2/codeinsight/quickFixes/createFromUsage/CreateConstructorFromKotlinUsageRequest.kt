// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateConstructorRequest
import org.jetbrains.kotlin.psi.KtCallElement

internal class CreateConstructorFromKotlinUsageRequest(
    call: KtCallElement,
    modifiers: Collection<JvmModifier>,
) : CreateExecutableFromKotlinUsageRequest<KtCallElement>(call, modifiers), CreateConstructorRequest