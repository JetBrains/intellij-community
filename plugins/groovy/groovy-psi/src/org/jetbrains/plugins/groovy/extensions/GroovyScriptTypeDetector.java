// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.search.GlobalSearchScope;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;

public abstract class GroovyScriptTypeDetector {

  public static final ExtensionPointName<GroovyScriptTypeDetector> EP_NAME = ExtensionPointName.create("org.intellij.groovy.scriptTypeDetector");

  private final GroovyScriptType myScriptType;

  protected GroovyScriptTypeDetector(@NotNull GroovyScriptType scriptType) {
    myScriptType = scriptType;
  }

  public final @NotNull GroovyScriptType getScriptType() {
    return myScriptType;
  }

  public abstract boolean isSpecificScriptFile(@NotNull GroovyFile script);

  public static @Nullable GroovyScriptType getScriptType(@NotNull GroovyFile file) {
    for (GroovyScriptTypeDetector detector : EP_NAME.getExtensions()) {
      if (detector.isSpecificScriptFile(file)) {
        return detector.getScriptType();
      }
    }
    return null;
  }

  public static boolean isScriptFile(@NotNull GroovyFile file) {
    return file.isScript() && getScriptType(file) != null;
  }

  public static @NotNull Icon getIcon(@NotNull GroovyFile file) {
    GroovyScriptType scriptType = getScriptType(file);
    return scriptType == null ? JetgroovyIcons.Groovy.GroovyFile : scriptType.getScriptIcon();
  }

  public static @NotNull GlobalSearchScope patchResolveScope(@NotNull GroovyFile script, @NotNull GlobalSearchScope scope) {
    GroovyScriptType scriptType = getScriptType(script);
    return scriptType == null ? scope : scriptType.patchResolveScope(script, scope);
  }
}
