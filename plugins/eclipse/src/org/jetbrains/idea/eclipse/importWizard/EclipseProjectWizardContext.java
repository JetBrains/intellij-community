// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.importWizard;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Kaznacheev
*/
interface EclipseProjectWizardContext {
  @Nullable
  String getRootDirectory();

  boolean setRootDirectory(@NotNull String path);
}
