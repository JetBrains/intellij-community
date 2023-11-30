// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl.toplevel.scopes;

import org.jetbrains.plugins.groovy.dsl.toplevel.AnnotatedContextFilter;
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"unused", "rawtypes"})
public class AnnotatedScope extends Scope {
  public AnnotatedScope(Map args) {
    annoQName = args == null ? null : (String)args.get("ctype");
  }

  @Override
  public List<ContextFilter> createFilters(Map args) {
    List<ContextFilter> result = new ArrayList<>();
    if (annoQName != null && !annoQName.isEmpty()) {
      result.add(new AnnotatedContextFilter(annoQName));
    }
    return result;
  }

  public final String getAnnoQName() {
    return annoQName;
  }

  private final String annoQName;
}
