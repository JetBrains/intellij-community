package org.jetbrains.jsonProtocol;

import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;

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

  public static void writeStringList(@NotNull JsonWriter writer, @NotNull String name, @NotNull Collection<String> value) throws IOException {
    writer.name(name).beginArray();
    for (String item : value) {
      writer.value(item);
    }
    writer.endArray();
  }
}