// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tag;

import git4idea.ui.GitTagDialog;
import org.jetbrains.annotations.NotNull;

public interface GitTagDialogHandler {
  enum ReturnResult {
    COMMIT, CANCEL
  }

  void createDialog(GitTagDialog dialog);

  @NotNull
  ReturnResult beforeCheckin();
}
