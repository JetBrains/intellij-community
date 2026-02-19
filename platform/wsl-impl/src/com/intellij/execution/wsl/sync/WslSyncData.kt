// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

/**
 * [hashes] is a list of [WslHashRecord] (file + hash)
 * [links] is a map of `source->target` links.
 * [stubs] is a list of target files to stub.
 */
data class WslSyncData(val hashes: List<WslHashRecord> = listOf(),
                       val links: Map<FilePathRelativeToDir, FilePathRelativeToDir> = mapOf(),
                       val stubs: Set<FilePathRelativeToDir> = setOf())
