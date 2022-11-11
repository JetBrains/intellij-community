// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaToKotlinConversionUtils")

package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.UserDataProperty

var VirtualFile.pathBeforeJavaToKotlinConversion: String? by UserDataProperty(Key.create("PATH_BEFORE_J2K_CONVERSION"))
    @ApiStatus.Internal get
    @ApiStatus.Internal set