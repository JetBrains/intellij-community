// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import com.intellij.util.io.Decompressor
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.nio.file.Path

@Deprecated(
  "use com.intellij.util.io.Decompressor.Tar instead",
  ReplaceWith("inputStream.use { Decompressor.Tar(it).extract(to.toFile()) }", "com.intellij.util.io.Decompressor"))
@ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
fun unpackTarGz(inputStream: InputStream, to: Path) =
  inputStream.use { Decompressor.Tar(it).extract(to.toFile()) }