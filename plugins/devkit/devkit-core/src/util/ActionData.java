// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author swr
 */
public interface ActionData {
  @NotNull
  String getActionId();

  @NotNull
  @NlsActions.ActionText
  String getActionText();

  @NlsActions.ActionDescription
  String getActionDescription();

  @Nullable
  String getSelectedGroupId();

  @Nullable
  String getSelectedActionId();

  String getSelectedAnchor();

  @Nullable
  String getFirstKeyStroke();

  @Nullable
  String getSecondKeyStroke();
}
