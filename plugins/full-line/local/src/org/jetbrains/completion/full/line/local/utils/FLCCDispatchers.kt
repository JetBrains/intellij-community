package org.jetbrains.completion.full.line.local.utils

import com.google.common.util.concurrent.ThreadFactoryBuilder
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

object FLCCDispatchers {
  val models = Executors.newFixedThreadPool(
    (Runtime.getRuntime().availableProcessors() - 1)
      .coerceAtLeast(1),
    ThreadFactoryBuilder()
      .setPriority(Thread.NORM_PRIORITY)
      .setNameFormat("flcc-models-thread-pool-%d")
      .setDaemon(true)
      .build()
  ).asCoroutineDispatcher()
}
