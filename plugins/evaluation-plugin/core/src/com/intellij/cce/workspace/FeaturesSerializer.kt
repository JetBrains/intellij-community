package com.intellij.cce.workspace

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.cce.core.Features

object FeaturesSerializer {
  private val gson = Gson()

  fun serialize(features: List<Features>): String = gson.toJson(features)

  fun deserialize(json: String): List<Features> {
    val type = object : TypeToken<List<Features>>() {}.type
    return gson.fromJson(json, type)
  }
}