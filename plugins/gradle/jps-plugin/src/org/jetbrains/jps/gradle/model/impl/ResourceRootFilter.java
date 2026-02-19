// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model.impl;

import com.dynatrace.hash4j.hashing.HashSink;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 */
@Tag("filter")
public final class ResourceRootFilter {
  @Tag("filterType") public @NotNull @NlsSafe String filterType;
  @Tag("properties") public @NotNull String properties;

  private transient Map<Object, Object> propertiesMap;

  public void computeConfigurationHash(@NotNull HashSink hash) {
    hash.putString(filterType);
    hash.putString(properties);
  }

  public @NotNull Map<Object, Object> getProperties() {
    if (propertiesMap == null) {
      try {
        Gson gson = new GsonBuilder().create();
        propertiesMap = gson.fromJson(
          properties,
          new TypeToken<Map<Object, Object>>() {
          }.getType());

        if ("RenamingCopyFilter".equals(filterType)) {
          final Object pattern = propertiesMap.get("pattern");
          final Matcher matcher = Pattern.compile(pattern instanceof String ? (String)pattern : "").matcher("");
          propertiesMap.put("matcher", matcher);
        }
      }
      catch (JsonParseException e) {
        throw new RuntimeException("Unsupported filter: " + properties, e);
      }
      if (propertiesMap == null) {
        propertiesMap = new HashMap<>();
      }
    }
    return propertiesMap;
  }
}
