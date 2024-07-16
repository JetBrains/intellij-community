package org.jetbrains.jsonProtocol

import com.google.gson.stream.JsonWriter
import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.Method

@ApiStatus.Internal
object JsonWriters {
  private val JSON_WRITE_DEFERRED_NAME: Method

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