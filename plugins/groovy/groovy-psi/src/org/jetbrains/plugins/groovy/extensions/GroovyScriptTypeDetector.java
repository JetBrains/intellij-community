// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.search.GlobalSearchScope;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;

/**
 * @author sergey.evdokimov
 */
public abstract class GroovyScriptTypeDetector {

  public static final ExtensionPointName<GroovyScriptTypeDetector> EP_NAME = ExtensionPointName.create("org.intellij.groovy.scriptTypeDetector");

  private final GroovyScriptType myScriptType;

  protected GroovyScriptTypeDetector(@NotNull GroovyScriptType scriptType) {
    myScriptType = scriptType;
  }

  @NotNull
  public final GroovyScriptType getScriptType() {
    return myScriptType;
  }

  public abstract boolean isSpecificScriptFile(@NotNull GroovyFile script);

  @Nullable
  public static GroovyScriptType getScriptType(@NotNull GroovyFile file) {
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

  @NotNull
  public static Icon getIcon(@NotNull GroovyFile file) {
    GroovyScriptType scriptType = getScriptType(file);
    return scriptType == null ? JetgroovyIcons.Groovy.GroovyFile : scriptType.getScriptIcon();
  }

  @NotNull
  public static GlobalSearchScope patchResolveScope(@NotNull GroovyFile script, @NotNull GlobalSearchScope scope) {
    GroovyScriptType scriptType = getScriptType(script);
    return scriptType == null ? scope : scriptType.patchResolveScope(script, scope);
  }
}
