package com.intellij.remoteDev.tests.impl

import org.jetbrains.annotations.ApiStatus
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val maxActionLength = 30

@ApiStatus.Internal
fun getActionNameAsFileNameSubstring(actionName: String): String {

  return getActionNameAsFileNameSubstring(actionName, LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss")))
}

@ApiStatus.Internal
fun getActionNameAsFileNameSubstring(actionName: String, timeStampString: String): String {

  return actionName
           .replace("[^a-zA-Z.]".toRegex(), "_")
           .replace("_+".toRegex(), "_")
           .take(maxActionLength) +
         "_at_$timeStampString"
}