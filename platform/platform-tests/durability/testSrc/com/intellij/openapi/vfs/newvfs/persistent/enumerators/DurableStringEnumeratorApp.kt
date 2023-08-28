// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.enumerators

import com.intellij.openapi.vfs.newvfs.persistent.App
import com.intellij.openapi.vfs.newvfs.persistent.AppAgent
import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableStringEnumerator
import java.nio.file.Path
import kotlin.io.path.absolute

class DurableStringEnumeratorApp: App {
  private class DSE : StringEnum {
    val instance = DurableStringEnumerator.open(Path.of("pse.data").absolute())

    override fun enumerate(s: String): Int {
      return instance.enumerate(s)
        //.also { instance.force() }
    }

    override fun valueOf(idx: Int): String? {
      try {
        return instance.valueOf(idx)
      } catch (_: IllegalArgumentException) {
        return null
      }
    }

    override fun flush() {
      return instance.force()
    }

    override fun close() {
      return instance.close()
    }
  }

  override fun run(appAgent: AppAgent) {
    val backend = DSE()
    StringEnumApp(backend).run(appAgent)
  }
}