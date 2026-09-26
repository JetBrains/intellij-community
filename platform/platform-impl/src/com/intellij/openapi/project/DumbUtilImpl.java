// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.platform.ide.productMode.IdeProductMode;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DumbUtilImpl implements DumbUtil {

  @Override
  public boolean mayUseIndices(@NotNull Project project) {
    return !DumbService.getInstance(project).isDumb() || FileBasedIndex.getInstance().getCurrentDumbModeAccessType(project) != null;
  }

  @Override
  public String chooseDumbModeMessage(@NotNull String nonLightModeMessage, @NotNull String lightModeMessage) {
    return IdeProductMode.isLight() ? lightModeMessage : nonLightModeMessage;
  }

  public static void waitForSmartMode(@Nullable Project project) {
    if (project != null) {
      DumbService.getInstance(project).waitForSmartMode();
    }
    else {
      for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
        DumbService.getInstance(openProject).waitForSmartMode();
      }
    }
  }
}
