// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.cachedValueProfiler;

import com.google.gson.stream.JsonReader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CVPReader {
  private CVPReader() {
  }

  @NotNull
  public static List<CVPInfo> deserialize(@NotNull InputStream stream) throws IOException {
    try (JsonReader reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return readJson(reader);
    }
  }

  public static class CVPInfo {
    public final String origin;
    public final long count;
    public final long cost;
    public final long used;
    public final long lifetime;

    public CVPInfo(@NotNull String origin, long count, long cost, long used, long lifetime) {
      this.origin = origin;
      this.count = count;
      this.cost = cost;
      this.used = used;
      this.lifetime = lifetime;
    }
  }

  @NotNull
  private static List<CVPInfo> readJson(JsonReader reader) throws IOException {
    ArrayList<CVPInfo> list = new ArrayList<>();
    reader.beginArray();
    while (reader.hasNext()) {
      list.add(readInfo(reader));
    }
    reader.endArray();

    return list;
  }

  @NotNull
  private static CVPInfo readInfo(@NotNull JsonReader reader) throws IOException {
    String origin = null;
    long count = 0;
    long cost = 0;
    long used = 0;
    long lifetime = 0;

    reader.beginObject();
    while (reader.hasNext()) {
      String name = reader.nextName();
      if ("origin".equals(name)) origin = reader.nextString();
      else if ("count".equals(name)) count = reader.nextLong();
      else if ("cost".equals(name)) cost = reader.nextLong();
      else if ("used".equals(name)) used = reader.nextLong();
      else if ("lifetime".equals(name)) lifetime = reader.nextLong();
      else throw new IOException("unexpected: " + name);
    }
    reader.endObject();
    if (origin == null) throw new IOException("origin is null");

    return new CVPInfo(origin, count, cost, used, lifetime);
  }
}
