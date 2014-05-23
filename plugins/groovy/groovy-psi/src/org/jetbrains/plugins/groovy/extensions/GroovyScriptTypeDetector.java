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
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.search.GlobalSearchScope;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;

/**
 * @author sergey.evdokimov
 */
public abstract class GroovyScriptTypeDetector {

  public static final ExtensionPointName<GroovyScriptTypeDetector> EP_NAME = ExtensionPointName.create("org.intellij.groovy.scriptTypeDetector");

  private final GroovyScriptType myScriptType;

  private final String[] myExtensions;

  protected GroovyScriptTypeDetector(GroovyScriptType scriptType, String ... extensions) {
    myScriptType = scriptType;
    myExtensions = extensions;
  }

  @NotNull
  public final String[] getExtensions() {
    return myExtensions;
  }

  @NotNull
  public final GroovyScriptType getScriptType() {
    return myScriptType;
  }

  public abstract boolean isSpecificScriptFile(@NotNull GroovyFile script);

  @NotNull
  public static Icon getIcon(@NotNull GroovyFile script) {
    for (GroovyScriptTypeDetector detector : EP_NAME.getExtensions()) {
      if (detector.isSpecificScriptFile(script)) {
        return detector.getScriptType().getScriptIcon();
      }
    }

    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  public static GlobalSearchScope patchResolveScope(@NotNull GroovyFile script, GlobalSearchScope scope) {
    for (GroovyScriptTypeDetector detector : EP_NAME.getExtensions()) {
      if (detector.isSpecificScriptFile(script)) {
        return detector.getScriptType().patchResolveScope(script, scope);
      }
    }
    return scope;
  }
}
