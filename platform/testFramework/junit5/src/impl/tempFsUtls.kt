// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import org.jetbrains.annotations.TestOnly
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path


private const val TMP_DIR_PREFIX = "IJTests"

@TestOnly
fun createTempDirectory(fs: FileSystem = FileSystems.getDefault()): Path = Files.createTempDirectory(fs.rootDirectories.first(), TMP_DIR_PREFIX)


