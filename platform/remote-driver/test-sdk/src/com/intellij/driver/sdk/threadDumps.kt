package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import kotlin.time.Duration

fun Driver.dumpThreads(
  folderName: String,
  fileNamePrefix: String,
) {
  service(ThreadDumpService::class).dumpThreads(folderName, fileNamePrefix)
}

fun Driver.withThreadDumps(
  folderName: String,
  fileNamePrefix: String,
  interval: Duration,
  body: () -> Unit,
) {
  val activityId = "$folderName-${System.currentTimeMillis()}"
  val dumpService = service(ThreadDumpService::class)
  try {
    dumpService.startDumpingThreads(
      activityId,
      folderName,
      fileNamePrefix,
      interval,
    )
    body()
  } finally {
    dumpService.stopDumpingThreads(activityId)
  }
}

@Remote(value = "com.jetbrains.performancePlugin.utils.ThreadDumpService")
interface ThreadDumpService {
  fun startDumpingThreads(
    activityId: String,
    folderName: String,
    fileNamePrefix: String,
    interval: Duration,
  )

  fun stopDumpingThreads(
    activityId: String,
  )

  fun dumpThreads(
    folderName: String,
    fileNamePrefix: String,
  )
}
