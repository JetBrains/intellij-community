package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class EclipseNatureImporter {
  public static final ExtensionPointName<EclipseNatureImporter> EP_NAME =
    ExtensionPointName.create("org.jetbrains.idea.eclipse.natureImporter");

  @NotNull
  public abstract String getNatureName();

  public abstract void doImport(@NotNull Project project, @NotNull List<Module> modules);
}
