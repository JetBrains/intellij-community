// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;

public abstract class GroovyScriptType {

  private final String id;

  protected GroovyScriptType(String id) {
    this.id = id;
  }

  public @NotNull String getId() {
    return id;
  }

  public abstract @NotNull Icon getScriptIcon();

  public GlobalSearchScope patchResolveScope(@NotNull GroovyFile file, @NotNull GlobalSearchScope baseScope) {
    return baseScope;
  }
}
