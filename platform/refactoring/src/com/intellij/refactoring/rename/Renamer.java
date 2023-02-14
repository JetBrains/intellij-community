// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
@ApiStatus.OverrideOnly
public interface Renamer {

  /**
   * @return text to be rendered in the choice popup if there are several renamers available
   */
  @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getPresentableText();

  /**
   * Performs rename using data obtained from context.
   * May show dialogs.
   */
  void performRename();
}