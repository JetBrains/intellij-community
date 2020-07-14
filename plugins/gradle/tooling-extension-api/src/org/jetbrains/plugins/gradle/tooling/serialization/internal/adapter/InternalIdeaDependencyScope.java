// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.gradle.tooling.model.idea.IdeaDependencyScope;

import java.util.HashMap;
import java.util.Map;

public final class InternalIdeaDependencyScope implements IdeaDependencyScope {
  private static final Map<String, InternalIdeaDependencyScope> SCOPES_MAP = new HashMap<String, InternalIdeaDependencyScope>();

  static {
    SCOPES_MAP.put("Compile", new InternalIdeaDependencyScope("Compile"));
    SCOPES_MAP.put("Test", new InternalIdeaDependencyScope("Test"));
    SCOPES_MAP.put("Runtime", new InternalIdeaDependencyScope("Runtime"));
    SCOPES_MAP.put("Provided", new InternalIdeaDependencyScope("Provided"));
  }

  private final String myScope;

  public InternalIdeaDependencyScope(String scope) {
    myScope = scope;
  }

  @Override
  public String getScope() {
    return myScope;
  }

  @Override
  public String toString() {
    return "IdeaDependencyScope{" +
           "myScope='" + myScope + '\'' +
           '}';
  }

  public static InternalIdeaDependencyScope getInstance(String scope) {
    InternalIdeaDependencyScope dependencyScope = SCOPES_MAP.get(scope == null || scope.isEmpty() ? "Compile" : scope);
    return dependencyScope == null ? new InternalIdeaDependencyScope(scope) : dependencyScope;
  }
}
