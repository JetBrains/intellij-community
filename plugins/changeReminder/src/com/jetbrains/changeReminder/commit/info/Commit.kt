package com.jetbrains.changeReminder.commit.info

import com.intellij.openapi.vcs.FilePath

data class Commit(val id: Int, val time: Long, val files: Set<FilePath>)