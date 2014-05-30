/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.runner;

import com.intellij.psi.PsiFile;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyRunnableScriptType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;

public class GroovyScriptUtil {
  public static final GroovyRunnableScriptType DEFAULT_TYPE = new GroovyRunnableScriptType("default") {
    @Override
    public boolean shouldBeCompiled(GroovyFile script) {
      return true;
    }

    @NotNull
    @Override
    public Icon getScriptIcon() {
      return JetgroovyIcons.Groovy.Groovy_16x16;
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
    for (GroovyScriptTypeDetector detector : GroovyScriptTypeDetector.EP_NAME.getExtensions()) {
      if (detector.isSpecificScriptFile(script)) {
        return (GroovyRunnableScriptType)detector.getScriptType();
      }
    }

    return DEFAULT_TYPE;
  }
}
