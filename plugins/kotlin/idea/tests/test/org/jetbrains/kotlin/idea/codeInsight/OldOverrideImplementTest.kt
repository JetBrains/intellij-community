// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import org.jetbrains.kotlin.idea.core.overrideImplement.OverrideMemberChooserObject
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class OldOverrideImplementTest : OverrideImplementTest<OverrideMemberChooserObject>(), OldOverrideImplementTestMixIn
