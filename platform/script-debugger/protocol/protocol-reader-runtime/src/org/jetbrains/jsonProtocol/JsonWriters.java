package org.jetbrains.jsonProtocol;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

public final class JsonWriters {
  public static final Method JSON_WRITE_DEFERRED_NAME;

  static {
    try {
      JSON_WRITE_DEFERRED_NAME = JsonWriter.class.getDeclaredMethod("writeDeferredName");
      JSON_WRITE_DEFERRED_NAME.setAccessible(true);
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private JsonWriters() {
  }

  public static void writeStringList(JsonWriter writer, String name, List<String> value) throws IOException {
    writer.name(name);
    writer.beginArray();
    for (String item : value) {
      writer.value(item);
    }
    writer.endArray();
  }
}