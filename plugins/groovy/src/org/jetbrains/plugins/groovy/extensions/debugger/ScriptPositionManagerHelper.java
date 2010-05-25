/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.extensions.debugger;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * Class to extend debugger functionality to handle various Groovy scripts
 *
 * @author ilyas
 */
public abstract class ScriptPositionManagerHelper {
  public static final ExtensionPointName<ScriptPositionManagerHelper> EP_NAME = ExtensionPointName.create("org.intellij.groovy.positionManagerDelegate");

  public abstract boolean isAppropriateRuntimeName(@NotNull String runtimeName);

  @NotNull
  public String getOriginalScriptName(ReferenceType refType, @NotNull final String runtimeName) {
    return runtimeName;
  }

  public abstract boolean isAppropriateScriptFile(@NotNull PsiFile scriptFile);

  /**
   * @return Runtime script name
   */
  @NotNull
  public abstract String getRuntimeScriptName(@NotNull String originalName, GroovyFile groovyFile);


  /**
   * @return Posiible script to debug through in project scope if there wer not found other by standarrd methods
   */
  @Nullable
  public abstract PsiFile getExtraScriptIfNotFound(ReferenceType refType, @NotNull String runtimeName, Project project);

  @Nullable
  public String customizeClassName(PsiClass psiClass) {
    return null;
  }
}
