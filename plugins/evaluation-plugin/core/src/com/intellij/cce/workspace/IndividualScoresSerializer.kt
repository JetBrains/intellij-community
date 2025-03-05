package com.intellij.cce.workspace

import com.google.gson.GsonBuilder
import com.intellij.cce.workspace.info.FileEvaluationDataInfo

class IndividualScoresSerializer {
  companion object {
    private val gson = GsonBuilder()
      .serializeNulls()
      .setPrettyPrinting()
      .create()
  }

  fun serialize(metrics: FileEvaluationDataInfo): String {
    return gson.toJson(metrics)
  }

  fun deserialize(json: String): FileEvaluationDataInfo {
    return gson.fromJson(json, FileEvaluationDataInfo::class.java)
  }
}
