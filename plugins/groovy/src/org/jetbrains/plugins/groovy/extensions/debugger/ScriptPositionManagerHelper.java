package org.jetbrains.plugins.groovy.extensions.debugger;

import com.intellij.psi.PsiFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class to extend debugger functionality to handle various Groovy scripts
 *
 * @author ilyas
 */
public interface ScriptPositionManagerHelper {

  boolean isAppropriateRuntimeName(@NotNull String runtimeName);

  /**
   * @return Original script name by modified runtime from runtime
   */
  @NotNull
  String getOriginalScriptName(@NotNull String runtimeName);

  boolean isAppropriateScriptFile(@NotNull PsiFile scriptFile);

  /**
   * @return Runtime script name
   */
  @NotNull
  String getRuntimeScriptName(@NotNull String originalName);


  /**
   * @return Posiible script to debug through in project scope if there wer not found other by standarrd methods
   */
  @Nullable
  PsiFile getExtraScriptIfNotFound(@NotNull String runtimeName, Project project);
}
