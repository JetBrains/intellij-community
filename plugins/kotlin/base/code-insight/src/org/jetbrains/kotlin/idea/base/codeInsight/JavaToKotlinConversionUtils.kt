// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaToKotlinConversionUtils")

package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.UserDataProperty

val pathBeforeJavaToKotlinConversion = Key.create<String>("PATH_BEFORE_J2K_CONVERSION")

@Deprecated("Use VirtualFile#putUserData(pathBeforeJavaToKotlinConversion) directly") // used in an external plugin
var VirtualFile.pathBeforeJavaToKotlinConversion: String? by UserDataProperty(pathBeforeJavaToKotlinConversion)
    @ApiStatus.Internal get
    @ApiStatus.Internal set