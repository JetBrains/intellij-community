// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.runner;

import com.intellij.psi.PsiFile;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyRunnableScriptType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;

public final class GroovyScriptUtil {
  public static final GroovyRunnableScriptType DEFAULT_TYPE = new GroovyRunnableScriptType("default") {
    @NotNull
    @Override
    public Icon getScriptIcon() {
      return JetgroovyIcons.Groovy.GroovyFile;
    }

    @Override
    public GroovyScriptRunner getRunner() {
      return new DefaultGroovyScriptRunner();
    }
  };

  public static boolean isSpecificScriptFile(@NotNull PsiFile file, GroovyScriptType scriptType) {
    if (!(file instanceof GroovyFile)) return false;
    if (!((GroovyFile)file).isScript()) return false;
    return isSpecificScriptFile((GroovyFile)file, scriptType);
  }

  public static boolean isSpecificScriptFile(@NotNull GroovyFile script, GroovyScriptType scriptType) {
    assert script.isScript();
    if (scriptType == DEFAULT_TYPE) {
      return getScriptType(script) == DEFAULT_TYPE;
    }

    for (GroovyScriptTypeDetector detector : GroovyScriptTypeDetector.EP_NAME.getExtensions()) {
      if (detector.getScriptType() == scriptType) {
        if (detector.isSpecificScriptFile(script)) {
          return true;
        }
      }
    }

    return false;
  }

  @NotNull
  public static GroovyRunnableScriptType getScriptType(@NotNull GroovyFile script) {
    GroovyScriptType scriptType = GroovyScriptTypeDetector.getScriptType(script);
    return scriptType == null ? DEFAULT_TYPE : (GroovyRunnableScriptType)scriptType;
  }

  public static boolean isPlainGroovyScript(@NotNull GroovyFile script) {
    return GroovyScriptTypeDetector.getScriptType(script) == null;
  }
}
