// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class OnboardingTipsInstallationInfo(val simpleSampleText: String, val fileName: String, val offsetForBreakpoint: (CharSequence) -> Int?)
