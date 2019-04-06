// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change

sealed class PredictedFile
class PredictedChange(val change: Change) : PredictedFile()
class PredictedFilePath(val filePath: FilePath) : PredictedFile()