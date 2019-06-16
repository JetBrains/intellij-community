/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The entry for rebase editor
 */
class GitRebaseEntry {
  private static final Logger LOG = Logger.getInstance(GitRebaseEntry.class);
  private final String myCommit;
  private final String mySubject;
  private Action myAction;

  GitRebaseEntry(String action, final String commit, final String subject) {
    this(Action.fromString(action), commit, subject);
  }

  GitRebaseEntry(Action action, String commit, String subject) {
    myCommit = commit;
    mySubject = subject;
    myAction = action;
  }

  public String getCommit() {
    return myCommit;
  }

  public String getSubject() {
    return mySubject;
  }

  public Action getAction() {
    return myAction;
  }

  public void setAction(@NotNull Action action) {
    myAction = action;
  }

  public enum Action {
    PICK("pick", 'p'),
    EDIT("edit", 'e'),
    SKIP("skip", 's'),
    SQUASH("squash", 'q'),
    REWORD("reword", 'r'),
    FIXUP("fixup", 'f');

    @NotNull private final String myText;
    private final char myMnemonic;

    Action(@NotNull String text, char mnemonic) {
      myText = text;
      myMnemonic = mnemonic;
    }

    public char getMnemonic() {
      return myMnemonic;
    }

    @Override
    public String toString() {
      return myText;
    }

    @NotNull
    static Action fromString(@NonNls @NotNull String actionName) {
      try {
        return valueOf(actionName.toUpperCase(Locale.ENGLISH));
      }
      catch (IllegalArgumentException e) {
        LOG.error(e);
        return PICK;
      }
    }
  }
}
