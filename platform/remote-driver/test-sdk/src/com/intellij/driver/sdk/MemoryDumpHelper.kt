package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.RdTarget

@Remote("com.intellij.util.MemoryDumpHelper")
interface MemoryDumpHelper {
  fun captureMemoryDump(dumpPath: String): Array<Module>
  fun captureMemoryDumpZipped(zipPath: String)
}

fun Driver.captureMemoryDump(dumpPath: String): Any = utility(MemoryDumpHelper::class).captureMemoryDump(dumpPath)

/** Captures a memory dump of [rdTarget] to the zip archive [dumpPath]. */
fun Driver.captureZippedMemoryDump(dumpPath: String, rdTarget: RdTarget = RdTarget.DEFAULT) {
  utility(MemoryDumpHelper::class, rdTarget).captureMemoryDumpZipped(dumpPath)
}