// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.extensions.debugger;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.Collection;
import java.util.Collections;

/**
 * Class to extend debugger functionality to handle various Groovy scripts
 */
public abstract class ScriptPositionManagerHelper {
  public static final ExtensionPointName<ScriptPositionManagerHelper> EP_NAME = ExtensionPointName.create("org.intellij.groovy.positionManagerDelegate");

  /**
   * @param runtimeName runtime name of class
   * @return true if extension may provide {@link ScriptPositionManagerHelper#getOriginalScriptName(ReferenceType, String) original}
   * fully qualified script name or find {@link PsiFile}
   * {@link ScriptPositionManagerHelper#getExtraScriptIfNotFound(ReferenceType, String, Project, GlobalSearchScope) corresponding}
   * to this runtime name
   */
  public boolean isAppropriateRuntimeName(@NotNull String runtimeName) {
    return false;
  }

  public @Nullable String getOriginalScriptName(@NotNull ReferenceType refType, final @NotNull String runtimeName) {
    return null;
  }

  /**
   * @return true if extension may compute runtime script name given script file
   */
  public boolean isAppropriateScriptFile(@NotNull GroovyFile scriptFile) {
    return false;
  }

  /**
   * @return Runtime script name
   */
  public @Nullable String getRuntimeScriptName(@NotNull GroovyFile groovyFile) {
    return null;
  }

  /**
   * @return Possible script to debug through in project scope if there wer not found other by standarrd methods
   */
  public @Nullable PsiFile getExtraScriptIfNotFound(@NotNull ReferenceType refType,
                                          @NotNull String runtimeName,
                                          @NotNull Project project,
                                          @NotNull GlobalSearchScope scope) {
    return null;
  }

  /**
   * @return fully qualified runtime class name
   * @see #getOriginalScriptName(ReferenceType, String)
   */
  public @Nullable String customizeClassName(@NotNull PsiClass psiClass) {
    return null;
  }

  /**
   * If a position manager works with scripts of FileType other,
   * then {@link org.jetbrains.plugins.groovy.GroovyFileType#GROOVY_FILE_TYPE}, it should report them here.
   * @return file types supported by this position manager helper
   */
  public Collection<? extends FileType> getAcceptedFileTypes() {
    return Collections.emptySet();
  }
}
