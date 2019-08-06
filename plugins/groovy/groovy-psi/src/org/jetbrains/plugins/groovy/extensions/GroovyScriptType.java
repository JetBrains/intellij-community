// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;

/**
 * @author ilyas
 */
public abstract class GroovyScriptType {

  private final String id;

  protected GroovyScriptType(String id) {
    this.id = id;
  }

  @NotNull
  public String getId() {
    return id;
  }

  @NotNull
  public abstract Icon getScriptIcon();

  public GlobalSearchScope patchResolveScope(@NotNull GroovyFile file, @NotNull GlobalSearchScope baseScope) {
    return baseScope;
  }
}
