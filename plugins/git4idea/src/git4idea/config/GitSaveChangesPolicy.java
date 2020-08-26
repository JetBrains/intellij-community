/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull private final String myTextKey;

  GitSaveChangesPolicy(@NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) String textKey) {
    myTextKey = textKey;
  }

  @Nls
  @NotNull
  public String getText() {
    return GitBundle.message(myTextKey);
  }

  public abstract @NotNull @Nls String selectBundleMessage(@NotNull @Nls String stashMessage, @NotNull @Nls String shelfMessage);
}
