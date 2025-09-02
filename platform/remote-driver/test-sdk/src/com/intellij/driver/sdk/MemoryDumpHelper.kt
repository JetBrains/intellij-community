package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

@Remote("com.intellij.util.MemoryDumpHelper")
interface MemoryDumpHelper {
  fun captureMemoryDump(dumpPath: String): Array<Module>
}

fun Driver.captureMemoryDump(dumpPath: String): Any = utility(MemoryDumpHelper::class).captureMemoryDump(dumpPath)