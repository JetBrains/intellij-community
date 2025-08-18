package com.intellij.grazie.utils

import java.lang.Boolean.getBoolean

private const val GRAZIE_IN_AI_ASSISTANT_FUNCTIONALLY_DISABLED_PROPERTY = "grazie.in.ai.assistant.functionally.disabled"

fun isFunctionallyDisabled(): Boolean {
  return getBoolean(GRAZIE_IN_AI_ASSISTANT_FUNCTIONALLY_DISABLED_PROPERTY)
}