// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public enum GitSaveChangesPolicy {
  STASH("local.changes.save.policy.stash") {
    @Override
    public @NotNull @Nls String selectBundleMessage(@NotNull @Nls String stashMessage, @NotNull @Nls String shelfMessage) {
      return stashMessage;
    }
  },
  SHELVE("local.changes.save.policy.shelve") {
    @Override
    public @NotNull @Nls String selectBundleMessage(@NotNull @Nls String stashMessage, @NotNull @Nls String shelfMessage) {
      return shelfMessage;
    }
  };

  private final @NotNull String myTextKey;

  GitSaveChangesPolicy(@NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) String textKey) {
    myTextKey = textKey;
  }

  public @Nls @NotNull String getText() {
    return GitBundle.message(myTextKey);
  }

  public abstract @NotNull @Nls String selectBundleMessage(@NotNull @Nls String stashMessage, @NotNull @Nls String shelfMessage);
}
