// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

internal class WslHashRecord(val file: String, val hash: Long, val fileLowerCase: String = file.lowercase())
