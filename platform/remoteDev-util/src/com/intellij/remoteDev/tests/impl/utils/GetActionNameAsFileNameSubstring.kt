package com.intellij.remoteDev.tests.impl

import org.jetbrains.annotations.ApiStatus

private val maxActionLength = 30

@ApiStatus.Internal
fun getActionNameAsFileNameSubstring(actionName: String): String {

  return actionName
    .replace("[^a-zA-Z.]".toRegex(), "_")
    .replace("_+".toRegex(), "_")
    .take(maxActionLength)
}