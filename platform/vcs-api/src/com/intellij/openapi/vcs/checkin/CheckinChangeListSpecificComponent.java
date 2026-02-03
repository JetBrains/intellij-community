// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import org.jetbrains.annotations.NotNull;

public interface CheckinChangeListSpecificComponent extends RefreshableOnComponent {
  void onChangeListSelected(@NotNull LocalChangeList list);
}
