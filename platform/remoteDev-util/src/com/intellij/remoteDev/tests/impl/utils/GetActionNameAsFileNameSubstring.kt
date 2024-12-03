package com.intellij.remoteDev.tests.impl.utils

import org.jetbrains.annotations.ApiStatus
import java.time.LocalTime
import java.time.format.DateTimeFormatter.ofPattern

private val maxActionLength = 30


@ApiStatus.Internal
fun getAsPartOfArtifactsFileName(text: String): String =
  text
    .replace("[^a-zA-Z.]".toRegex(), "_")
    .replace("_+".toRegex(), "_")

@ApiStatus.Internal
fun getArtifactsFileName(actionName: String, suffix: String? = null, extension: String? = null, timeStamp: LocalTime = LocalTime.now()): String =
  buildString {
    append(getAsPartOfArtifactsFileName(actionName)
             .take(maxActionLength))
    append(suffix?.let { "-$it" }.orEmpty())
    append("-at_${timeStamp.format(ofPattern("HHmmss"))}")
    if (extension != null) {
      append(".$extension")
    }
  }