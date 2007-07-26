package org.jetbrains.idea.eclipse.action;

import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Kaznacheev
*/
interface EclipseProjectWizardContext {
  @Nullable
  String getRootDirectory();

  boolean setRootDirectory(String path);
}
