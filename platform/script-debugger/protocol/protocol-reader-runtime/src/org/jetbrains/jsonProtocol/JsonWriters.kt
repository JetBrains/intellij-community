package org.jetbrains.jsonProtocol

import com.google.gson.stream.JsonWriter
import java.lang.reflect.Method

object JsonWriters {
  val JSON_WRITE_DEFERRED_NAME: Method

  init {
      JSON_WRITE_DEFERRED_NAME = JsonWriter::class.java.getDeclaredMethod("writeDeferredName")
      JSON_WRITE_DEFERRED_NAME.isAccessible = true
  }

  fun writeStringList(writer: JsonWriter, name: String, value: Collection<String>) {
    writer.name(name).beginArray()
    for (item in value) {
      writer.value(item)
    }
    writer.endArray()
  }
}