package com.intellij.cce.workspace.storages

interface LogsSaver {
  fun <T> invokeRememberingLogs(action: () -> T): T

  fun save(languageName: String?, trainingPercentage: Int)
}

class NoLogsSaver : LogsSaver {
  override fun <T> invokeRememberingLogs(action: () -> T): T = action()

  override fun save(languageName: String?, trainingPercentage: Int) = Unit
}

fun logsSaverIf(condition: Boolean, createSaver: () -> LogsSaver): LogsSaver = if (condition) createSaver() else NoLogsSaver()

private fun makeNestingCollector(saverA: LogsSaver,
                                 saverB: LogsSaver
) = object : LogsSaver {
  override fun <T> invokeRememberingLogs(action: () -> T): T {
    return saverA.invokeRememberingLogs {
      saverB.invokeRememberingLogs(action)
    }
  }

  override fun save(languageName: String?, trainingPercentage: Int) {
    try {
      saverA.save(languageName, trainingPercentage)
    }
    finally {
      saverB.save(languageName, trainingPercentage)
    }
  }
}

fun Collection<LogsSaver>.asCompositeLogsSaver(): LogsSaver {
  val meaningfulSavers = this@asCompositeLogsSaver.filter { it !is NoLogsSaver }
  val meaningfulLogsSavers: List<LogsSaver> = meaningfulSavers.ifEmpty { listOf(NoLogsSaver()) }

  return meaningfulLogsSavers.reduce(::makeNestingCollector)
}
