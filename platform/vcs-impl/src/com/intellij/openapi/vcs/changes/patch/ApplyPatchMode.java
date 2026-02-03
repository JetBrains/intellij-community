// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsBundle;

public enum ApplyPatchMode {
  APPLY(VcsBundle.message("patch.apply.dialog.title"), true),
  UNSHELVE(VcsBundle.message("unshelve.changes.dialog.title"), false),
  APPLY_PATCH_IN_MEMORY(VcsBundle.message("patch.apply.dialog.title"), false);

  private final @NlsContexts.DialogTitle String myTitle;
  private final boolean myCanChangePatchFile;

  ApplyPatchMode(@NlsContexts.DialogTitle String title, boolean canChangePatchFile) {
    myTitle = title;
    myCanChangePatchFile = canChangePatchFile;
  }

  public @NlsContexts.DialogTitle String getTitle() {
    return myTitle;
  }

  public boolean isCanChangePatchFile() {
    return myCanChangePatchFile;
  }
}
