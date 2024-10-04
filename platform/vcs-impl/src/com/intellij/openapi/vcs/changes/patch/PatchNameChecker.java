// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.*;

import java.io.File;

@ApiStatus.Internal
public final class PatchNameChecker {
  public final static int MAX = 100;
  private final static int MAX_PATH = 255; // Windows path len restrictions

  @Nls
  @Nullable
  public static String validateName(@NotNull @NonNls String name) {
    String fileName = new File(name).getName();
    if (StringUtil.isEmptyOrSpaces(fileName)) {
      return IdeBundle.message("error.name.cannot.be.empty");
    }
    else if (name.length() > MAX_PATH) {
      return VcsBundle.message("patch.creation.name.too.long.error");
    }
    return null;
  }
}
