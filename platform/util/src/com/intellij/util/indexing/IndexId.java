// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class IndexId<K, V> {
  private static final Logger LOG = Logger.getInstance(IndexId.class);

  private static final Map<String, IndexId<?, ?>> ourInstances = new HashMap<>();

  public static final String VALID_ID_REGEXP = "[A-Za-z0-9_.\\-]+";
  private static final java.util.regex.Pattern VALID_ID_REGEXP_PATTERN = java.util.regex.Pattern.compile(VALID_ID_REGEXP);

  private final @NotNull String myName;


  protected IndexId(@NotNull @Pattern(VALID_ID_REGEXP) String name) {
    if (!VALID_ID_REGEXP_PATTERN.matcher(name).matches()) {
      LOG.warn("IndexId name[" + name + "] should match [" + VALID_ID_REGEXP + "]. " +
               "Names with unsafe characters could cause issues on some platforms. " +
               "This warning likely will be escalated to an error in the following releases.");
      //TODO RC: move to exception in v23.3?
      //throw new IllegalArgumentException("name[" + name + "] must match [" + VALID_ID_REGEXP + "]");
    }
    myName = name;
  }

  public final @NotNull String getName() {
    return myName;
  }

  /**
   * Consider to use {@link ID#getName()} instead of this method
   */
  @Override
  public String toString() {
    return getName();
  }

  public static <K, V> IndexId<K, V> create(String name) {
    synchronized (ourInstances) {
      @SuppressWarnings("unchecked")
      IndexId<K, V> id = (IndexId<K, V>)ourInstances.get(name);
      if (id == null) {
        ourInstances.put(name, id = new IndexId<>(name));
      }
      return id;
    }
  }
}
