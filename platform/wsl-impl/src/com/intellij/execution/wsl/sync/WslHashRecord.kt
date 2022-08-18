// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

class WslHashRecord(val file: FilePathRelativeToDir, val hash: Long, val fileLowerCase: FilePathRelativeToDir = FilePathRelativeToDir(file.toString().lowercase()))
