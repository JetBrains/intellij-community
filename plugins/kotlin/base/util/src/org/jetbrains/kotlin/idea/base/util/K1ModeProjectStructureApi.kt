// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util

@RequiresOptIn("Kotlin K1 mode API. It cannot be used in K2 or shared code (for both K1 and K2). Use `org.jetbrains.kotlin.analysis.api.projectStructure.KaModule` instead.", level = RequiresOptIn.Level.ERROR)
annotation class K1ModeProjectStructureApi