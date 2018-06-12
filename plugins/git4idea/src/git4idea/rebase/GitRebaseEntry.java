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
  /**
   * the logger
   */
  private static final Logger log = Logger.getInstance(GitRebaseEntry.class.getName());
  /**
   * The commit hash
   */
  private final String myCommit;
  /**
   * The commit comment subject line
   */
  private final String mySubject;
  /**
   * The action associated with the entry
   */
  private Action myAction;

  /**
   * The constructor
   *
   * @param action
   * @param commit  the commit hash
   * @param subject the commit subject
   */
  public GitRebaseEntry(String action, final String commit, final String subject) {
    this(Action.fromString(action), commit, subject);
  }

  public GitRebaseEntry(Action action, String commit, String subject) {
    myCommit = commit;
    mySubject = subject;
    myAction = action;
  }

  /**
   * @return the commit hash
   */
  public String getCommit() {
    return myCommit;
  }

  /**
   * @return the commit subject
   */
  public String getSubject() {
    return mySubject;
  }

  /**
   * @return the action associated with the commit
   */
  public Action getAction() {
    return myAction;
  }

  /**
   * @param action a new action to set
   */
  public void setAction(final Action action) {
    if (action == null) {
      log.error("The action must not be null");
    }
    else {
      myAction = action;
    }
  }


  /**
   * The action associated with the commit
   */
  public enum Action {
    /**
     * the pick action
     */
    PICK("pick", 'p'),
    /**
     * the edit action, the user will be offered to alter commit
     */
    EDIT("edit", 'e'),
    /**
     * the skip action
     */
    SKIP("skip", 's'),
    /**
     * the squash action (for two or more commits)
     */
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
        log.error(e);
        return PICK;
      }
    }
  }
}
