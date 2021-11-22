// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UIWizardUtil")

package com.intellij.ide.wizard

import com.intellij.openapi.util.io.FileUtil
import java.io.File


fun getPresentablePath(path: String): String {
  return FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path.trim()), false)
}

fun getCanonicalPath(path: String, removeLastSlash: Boolean = true): String {
  return FileUtil.toCanonicalPath(FileUtil.expandUserHome(path.trim()), File.separatorChar, removeLastSlash)
}