// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

interface KotlinAndroidSourceSetInfo : Serializable {
    val kotlinSourceSetName: String
    val androidSourceSetName: String
    val androidVariantNames: Set<String>
}