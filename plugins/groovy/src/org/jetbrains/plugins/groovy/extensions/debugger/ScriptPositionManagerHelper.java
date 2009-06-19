package org.jetbrains.plugins.groovy.extensions.debugger;

import com.intellij.psi.PsiFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * Class to extend debugger functionality to handle various Groovy scripts
 *
 * @author ilyas
 */
public interface ScriptPositionManagerHelper {
  ExtensionPointName<ScriptPositionManagerHelper> EP_NAME = ExtensionPointName.create("org.intellij.groovy.positionManagerDelegate");

  boolean isAppropriateRuntimeName(@NotNull String runtimeName);

  /**
   * @return Original script name by modified runtime from runtime
   */
  @NotNull
  String getOriginalScriptName(ReferenceType refType, @NotNull String runtimeName);

  boolean isAppropriateScriptFile(@NotNull PsiFile scriptFile);

  /**
   * @return Runtime script name
   */
  @NotNull
  String getRuntimeScriptName(@NotNull String originalName, GroovyFile groovyFile);


  /**
   * @return Posiible script to debug through in project scope if there wer not found other by standarrd methods
   */
  @Nullable
  PsiFile getExtraScriptIfNotFound(ReferenceType refType, @NotNull String runtimeName, Project project);
}
