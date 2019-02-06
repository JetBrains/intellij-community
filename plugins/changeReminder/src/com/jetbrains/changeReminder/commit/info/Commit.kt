// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.commit.info

import com.intellij.openapi.vcs.FilePath

data class Commit(val id: Int, val time: Long, val files: Set<FilePath>)