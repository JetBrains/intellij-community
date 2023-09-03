// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.enumerators

import com.intellij.openapi.vfs.newvfs.persistent.App
import com.intellij.openapi.vfs.newvfs.persistent.AppAgent
import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableStringEnumerator
import java.nio.file.Path
import kotlin.io.path.absolute

@Suppress("unused")
class DurableStringEnumeratorApp : App {

  private class DSE : StringEnum {
    //Each App is running in its own process, and process pwd set to temporary dir,
    // hence 'pse.data' file will be in unique folder each time.
    val instance = DurableStringEnumerator.open(Path.of("pse.data").absolute())

    override fun tryEnumerate(s: String): Int = instance.tryEnumerate(s)

    override fun enumerate(s: String): Int = instance.enumerate(s)

    override fun valueOf(idx: Int): String? = instance.valueOf(idx)

    override fun flush() = instance.force()

    override fun close() = instance.close()
  }

  override fun run(appAgent: AppAgent) {
    val backend = DSE()
    StringEnumeratorAppHelper(backend).run(appAgent)
  }
}