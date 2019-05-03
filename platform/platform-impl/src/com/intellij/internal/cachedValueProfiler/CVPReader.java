// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.cachedValueProfiler;

import com.google.gson.stream.JsonReader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CVPReader {
  @NotNull private final JsonReader myReader;

  private CVPReader(@NotNull JsonReader reader) {
    myReader = reader;
  }

  @NotNull
  public static List<CVPInfo> deserialize(@NotNull InputStream stream) throws IOException {
    try (JsonReader reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return new CVPReader(reader).read();
    }
  }

  @NotNull
  private List<CVPInfo> read() throws IOException {
    ArrayList<CVPInfo> list = new ArrayList<>();
    myReader.beginArray();
    while (myReader.hasNext()) {
      list.add(readInfo());
    }
    myReader.endArray();

    return list;
  }

  @NotNull
  private CVPInfo readInfo() throws IOException {
    String origin = null;
    long totalLifeTime = 0;
    long totalUseCount = 0;
    long createdCount = 0;

    myReader.beginObject();
    while (myReader.hasNext()) {
      String name = myReader.nextName();
      if ("origin".equals(name)) {
        origin = myReader.nextString();
      }
      else if ("total lifetime".equals(name)) {
        totalLifeTime = myReader.nextLong();
      }
      else if ("total use count".equals(name)) {
        totalUseCount = myReader.nextLong();
      }
      else if ("created".equals(name)) {
        createdCount = myReader.nextLong();
      }
    }
    myReader.endObject();

    return new CVPInfo(origin, totalLifeTime, totalUseCount, createdCount);
  }
}
