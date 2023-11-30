package com.intellij.remoteDev.tests.impl

import org.jetbrains.annotations.ApiStatus
import java.time.LocalTime
import java.time.format.DateTimeFormatter.ofPattern

private val maxActionLength = 30

@ApiStatus.Internal
fun getArtifactsFileName(actionName: String, suffix: String? = null, extension: String, timeStamp: LocalTime = LocalTime.now()): String =
  buildString {
    append(actionName
             .replace("[^a-zA-Z.]".toRegex(), "_")
             .replace("_+".toRegex(), "_")
             .take(maxActionLength))
    append(suffix?.let { "-$it" }.orEmpty())
    append("-at_${timeStamp.format(ofPattern("HHmmss"))}")
    append(".$extension")
  }