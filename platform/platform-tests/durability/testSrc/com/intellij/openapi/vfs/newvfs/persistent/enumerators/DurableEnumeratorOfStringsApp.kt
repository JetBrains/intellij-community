// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.enumerators

import com.intellij.openapi.vfs.newvfs.persistent.App
import com.intellij.openapi.vfs.newvfs.persistent.AppAgent
import com.intellij.openapi.vfs.newvfs.persistent.ExecuteOnThreadPool
import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableEnumeratorFactory
import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableStringEnumerator
import com.intellij.util.io.DurableDataEnumerator
import com.intellij.util.io.dev.enumerator.StringAsUTF8
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.absolute

@Suppress("unused")
class DurableEnumeratorOfStringsApp : App {

  private val durableEnumeratorFactory: ((Path) -> DurableDataEnumerator<String>)

  init {
    val enumeratorImpl = System.getProperty("durable-enumerator-impl", "durable-enumerator")
    //println("enumeratorImpl: ${enumeratorImpl}")
    System.err.println("enumeratorImpl: ${enumeratorImpl}")
    durableEnumeratorFactory = when (enumeratorImpl) {
      "durable-string-enumerator" -> { storagePath ->
        DurableStringEnumerator.open(storagePath)
      }
      "durable-string-enumerator-async" -> { storagePath ->
        DurableStringEnumerator.openAsync(storagePath, ExecuteOnThreadPool(Executors.newSingleThreadExecutor()))
      }
      "durable-enumerator-map-in-memory" -> { storagePath ->
        DurableEnumeratorFactory
          .defaultWithInMemoryMap(StringAsUTF8.INSTANCE)
          .open(storagePath)
      }
      else -> { storagePath -> //"durable-enumerator"
        DurableEnumeratorFactory
          .defaultWithDurableMap(StringAsUTF8.INSTANCE)
          .open(storagePath)
      }
    }
  }

  override fun run(appAgent: AppAgent) {
    //Each App is running in its own process, and process pwd set to temporary dir,
    // hence 'pse.data' file will be in unique folder each time.
    val storagePath = Path.of("pse.data").absolute()
    val durableEnumerator = durableEnumeratorFactory(storagePath)

    val backend = StringEnumBackendForDurableEnumerator(durableEnumerator)
    StringEnumeratorAppHelper(backend)
      .run(appAgent)
  }
}

class StringEnumBackendForDurableEnumerator(private val durableEnumerator: DurableDataEnumerator<String>) : StringEnum {

  override fun tryEnumerate(s: String): Int = durableEnumerator.tryEnumerate(s)

  override fun enumerate(s: String): Int = durableEnumerator.enumerate(s)

  override fun valueOf(idx: Int): String? = durableEnumerator.valueOf(idx)

  override fun flush() = durableEnumerator.force()

  override fun close() = durableEnumerator.close()
}