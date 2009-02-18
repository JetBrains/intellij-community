package org.jetbrains.idea.eclipse.importWizard;

import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Kaznacheev
*/
interface EclipseProjectWizardContext {
  @Nullable
  String getRootDirectory();

  boolean setRootDirectory(String path);
}
