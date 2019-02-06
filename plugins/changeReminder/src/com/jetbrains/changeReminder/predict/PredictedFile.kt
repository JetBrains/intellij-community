package com.jetbrains.changeReminder.predict

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change

sealed class PredictedFile
class PredictedChange(val change: Change) : PredictedFile()
class PredictedFilePath(val filePath: FilePath) : PredictedFile()