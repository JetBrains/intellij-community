package org.jetbrains.completion.full.line.language

import com.google.gson.annotations.SerializedName

enum class KeepKind {
  @SerializedName("short")
  SHORT,

  @SerializedName("prob")
  PROBABLE,

  @SerializedName("long")
  LONG,
}
