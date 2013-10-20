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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;

public class IncomingChangeState {
  private static final Logger INCOMING_LOG = Logger.getInstance("#incoming");

  private final FileStatus myStatus;
  private final FilePath myPath;
  private final String myRevision;
  private final State myState;

  public IncomingChangeState(final Change change, final String revision, State state) {
    myRevision = revision;
    myStatus = change.getFileStatus();
    myPath = ChangesUtil.getFilePath(change);
    myState = state;
  }

  public static void header(final String location) {
    INCOMING_LOG.debug("[------------- " + location + " --------------]");
  }

  public static void footer() {
    INCOMING_LOG.debug("----------------------------");
  }

  public void logSelf() {
    INCOMING_LOG.debug(myPath.getPresentableUrl() + " (" + myRevision + "): " + myStatus.getText() + " -> " + myState.myAccounted + " [" + myState.myName +
        "] " + myState.myCode);
  }

  public static enum State {
    AFTER_DOES_NOT_MATTER_NON_LOCAL("Skipped", true, 101),
    AFTER_DOES_NOT_MATTER_OUTSIDE_INCOMING("Skipped", true, 102),
    AFTER_DOES_NOT_MATTER_ALIEN_PATH("Skipped", true, 103),
    AFTER_DOES_NOT_MATTER_DELETED_FOUND_IN_INCOMING_LIST("deleted further", true,
                                                         104),

    AFTER_EXISTS_LOCALLY_AVAILABLE("change found", true, 105),
    AFTER_EXISTS_REVISION_NOT_LOADED("versioned item not found", false, 106),
    AFTER_EXISTS_NOT_LOCALLY_AVAILABLE("change not found", false, 107),

    AFTER_NOT_EXISTS_LOCALLY_AVAILABLE("not exists but locally available", true, 108),
    AFTER_NOT_EXISTS_MARKED_FOR_DELETION("marked for deletion", true, 109),
    AFTER_NOT_EXISTS_SUBSEQUENTLY_DELETED("subsequently deleted", true, 110),
    AFTER_NOT_EXISTS_OTHER("item missing", false, 111),

    BEFORE_DOES_NOT_MATTER_OUTSIDE("Skipped", true, 201),
    BEFORE_NOT_EXISTS_DELETED_LOCALLY("locally deleted", false, 202),
    BEFORE_NOT_EXISTS_ALREADY_DELETED("already deleted", true, 203),
    BEFORE_UNVERSIONED_INSTEAD_OF_VERS_DELETED("unversioned instead of versioned", true, 204),
    BEFORE_SAME_NAME_ADDED_AFTER_DELETION("item replaced", true, 205),
    BEFORE_EXISTS_BUT_SHOULD_NOT("NOT deleted", false, 206);

    private final boolean myAccounted;
    private final String myName;
    private final int myCode;

    private State(final String name, final boolean accounted, final int code) {
      myName = name;
      myAccounted = accounted;
      myCode = code;
    }
  }
}
