// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.core.util

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.CopyablePsiUserDataProperty
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.KotlinType

object CodeFragmentUtils {
    val RUNTIME_TYPE_EVALUATOR: Key<Function1<KtExpression, KotlinType?>> = Key.create("RUNTIME_TYPE_EVALUATOR")
}

var KtCodeFragment.externalDescriptors: List<DeclarationDescriptor>? by CopyablePsiUserDataProperty(Key.create("EXTERNAL_DESCRIPTORS"))
