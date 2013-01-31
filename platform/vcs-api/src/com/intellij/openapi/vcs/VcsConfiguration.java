/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.ide.todo.TodoPanelSettings;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.IgnoreSpaceEnum;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */

@State(
  name = "VcsManagerConfiguration",
  storages = {
    @Storage(
      file = StoragePathMacros.WORKSPACE_FILE
    )
    }
)
public final class VcsConfiguration implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.VcsConfiguration");
  public final static long ourMaximumFileForBaseRevisionSize = 500 * 1000;

  @NonNls static final String VALUE_ATTR = "value";
  @NonNls private static final String CONFIRM_MOVE_TO_FAILED_COMMIT_ELEMENT = "confirmMoveToFailedCommit";
  @NonNls private static final String CONFIRM_REMOVE_EMPTY_CHANGELIST_ELEMENT = "confirmRemoveEmptyChangelist";

  @NonNls public static final String PATCH = "patch";
  @NonNls public static final String DIFF = "diff";

  private Project myProject;

  public boolean OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT = true;
  public boolean CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT = !PlatformUtils.isPyCharm() && !PlatformUtils.isRubyMine();
  public boolean CHECK_NEW_TODO = true;
  public TodoPanelSettings myTodoPanelSettings = new TodoPanelSettings();
  public boolean PERFORM_UPDATE_IN_BACKGROUND = true;
  public boolean PERFORM_COMMIT_IN_BACKGROUND = true;
  public boolean PERFORM_EDIT_IN_BACKGROUND = true;
  public boolean PERFORM_CHECKOUT_IN_BACKGROUND = true;
  public boolean PERFORM_ADD_REMOVE_IN_BACKGROUND = true;
  public boolean PERFORM_ROLLBACK_IN_BACKGROUND = false;
  public volatile boolean CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND = false;
  public VcsShowConfirmationOption.Value MOVE_TO_FAILED_COMMIT_CHANGELIST = VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
  public VcsShowConfirmationOption.Value REMOVE_EMPTY_INACTIVE_CHANGELISTS = VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
  public int CHANGED_ON_SERVER_INTERVAL = 60;
  public boolean SHOW_ONLY_CHANGED_IN_SELECTION_DIFF = true;
  public boolean CHECK_COMMIT_MESSAGE_SPELLING = true;
  public String DEFAULT_PATCH_EXTENSION = PATCH;
  public boolean SHORT_DIFF_HORISONTALLY = true;
  public int SHORT_DIFF_EXTRA_LINES = 2;
  public boolean SOFT_WRAPS_IN_SHORT_DIFF = true;
  public IgnoreSpaceEnum SHORT_DIFF_IGNORE_SPACE = IgnoreSpaceEnum.NO;
  // asked only for non-DVCS
  public boolean INCLUDE_TEXT_INTO_PATCH = false;
  public boolean INCLUDE_TEXT_INTO_SHELF = false;
  public Boolean SHOW_PATCH_IN_EXPLORER = null;
  public boolean SHOW_FILE_HISTORY_DETAILS = true;
  public boolean SHOW_VCS_ERROR_NOTIFICATIONS = true;
  public boolean SHOW_DIRTY_RECURSIVELY = false;
  public boolean LIMIT_HISTORY = true;
  public int MAXIMUM_HISTORY_ROWS = 1000;

  public enum StandardOption {
    ADD(VcsBundle.message("vcs.command.name.add")),
    REMOVE(VcsBundle.message("vcs.command.name.remove")),
    EDIT(VcsBundle.message("vcs.command.name.edit")),
    CHECKOUT(VcsBundle.message("vcs.command.name.checkout")),
    STATUS(VcsBundle.message("vcs.command.name.status")),
    UPDATE(VcsBundle.message("vcs.command.name.update"));

    StandardOption(final String id) {
      myId = id;
    }

    private final String myId;

    public String getId() {
      return myId;
    }
  }

  public enum StandardConfirmation {
    ADD(VcsBundle.message("vcs.command.name.add")),
    REMOVE(VcsBundle.message("vcs.command.name.remove"));

    StandardConfirmation(final String id) {
      myId = id;
    }

    private final String myId;

    public String getId() {
      return myId;
    }
  }

  public boolean FORCE_NON_EMPTY_COMMENT = false;
  public boolean CLEAR_INITIAL_COMMIT_MESSAGE = false;

  private final ArrayList<String> myLastCommitMessages = new ArrayList<String>();
  public String LAST_COMMIT_MESSAGE = null;
  public boolean MAKE_NEW_CHANGELIST_ACTIVE = false;

  public boolean OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = false;
  public boolean CHECK_FILES_UP_TO_DATE_BEFORE_COMMIT = false;

  public boolean REFORMAT_BEFORE_PROJECT_COMMIT = false;
  public boolean REFORMAT_BEFORE_FILE_COMMIT = false;

  public float FILE_HISTORY_DIALOG_COMMENTS_SPLITTER_PROPORTION = 0.8f;
  public float FILE_HISTORY_DIALOG_SPLITTER_PROPORTION = 0.5f;

  public String ACTIVE_VCS_NAME;
  public boolean UPDATE_GROUP_BY_PACKAGES = false;
  public boolean UPDATE_GROUP_BY_CHANGELIST = false;
  public boolean SHOW_FILE_HISTORY_AS_TREE = false;
  public float FILE_HISTORY_SPLITTER_PROPORTION = 0.6f;
  private static final int MAX_STORED_MESSAGES = 25;
  @NonNls static final String MESSAGE_ELEMENT_NAME = "MESSAGE";

  private final PerformInBackgroundOption myUpdateOption = new UpdateInBackgroundOption();
  private final PerformInBackgroundOption myCommitOption = new CommitInBackgroundOption();
  private final PerformInBackgroundOption myEditOption = new EditInBackgroundOption();
  private final PerformInBackgroundOption myCheckoutOption = new CheckoutInBackgroundOption();
  private final PerformInBackgroundOption myAddRemoveOption = new AddRemoveInBackgroundOption();

  private VcsConfiguration() {
  }

  public VcsConfiguration(final Project project) {
    myProject = project;
  }

  public Element getState() {
    try {
      final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    Element child = element.getChild(CONFIRM_MOVE_TO_FAILED_COMMIT_ELEMENT);
    if (child != null) {
      MOVE_TO_FAILED_COMMIT_CHANGELIST = VcsShowConfirmationOption.Value.fromString(child.getAttributeValue(VALUE_ATTR));
    }
    child = element.getChild(CONFIRM_REMOVE_EMPTY_CHANGELIST_ELEMENT);
    if (child != null) {
      REMOVE_EMPTY_INACTIVE_CHANGELISTS = VcsShowConfirmationOption.Value.fromString(child.getAttributeValue(VALUE_ATTR));
    }
    final List messages = element.getChildren(MESSAGE_ELEMENT_NAME);
    for (final Object message : messages) {
      saveCommitMessage(((Element)message).getAttributeValue(VALUE_ATTR));
    }
    if (ACTIVE_VCS_NAME != null && ACTIVE_VCS_NAME.length() > 0) {
      StartupManager.getInstance(myProject).registerStartupActivity(new Runnable() {
        public void run() {
          ProjectLevelVcsManager.getInstance(myProject).setDirectoryMapping("", ACTIVE_VCS_NAME);
        }
      });
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    if (MOVE_TO_FAILED_COMMIT_CHANGELIST != VcsShowConfirmationOption.Value.SHOW_CONFIRMATION) {
      Element confirmChild = new Element(CONFIRM_MOVE_TO_FAILED_COMMIT_ELEMENT);
      confirmChild.setAttribute(VALUE_ATTR, MOVE_TO_FAILED_COMMIT_CHANGELIST.toString());
      element.addContent(confirmChild);
    }
    if (REMOVE_EMPTY_INACTIVE_CHANGELISTS != VcsShowConfirmationOption.Value.SHOW_CONFIRMATION) {
      Element confirmChild = new Element(CONFIRM_REMOVE_EMPTY_CHANGELIST_ELEMENT);
      confirmChild.setAttribute(VALUE_ATTR, REMOVE_EMPTY_INACTIVE_CHANGELISTS.toString());
      element.addContent(confirmChild);
    }
    for (String message : myLastCommitMessages) {
      final Element messageElement = new Element(MESSAGE_ELEMENT_NAME);
      messageElement.setAttribute(VALUE_ATTR, message);
      element.addContent(messageElement);
    }
  }

  public static VcsConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, VcsConfiguration.class);
  }

  public void saveCommitMessage(final String comment) {

    LAST_COMMIT_MESSAGE = comment;

    if (comment == null || comment.length() == 0) return;

    myLastCommitMessages.remove(comment);

    while (myLastCommitMessages.size() >= MAX_STORED_MESSAGES) {
      myLastCommitMessages.remove(0);
    }

    myLastCommitMessages.add(comment);
  }

  /**
   * @deprecated Use {@link #getLastNonEmptyCommitMessage()} instead.
   */
  public String getLastCommitMessage() {
    return getLastNonEmptyCommitMessage();
  }

  public String getLastNonEmptyCommitMessage() {
    if (myLastCommitMessages.isEmpty()) {
      return null;
    }
    else {
      return myLastCommitMessages.get(myLastCommitMessages.size() - 1);
    }
  }

  @NotNull
  public ArrayList<String> getRecentMessages() {
    return new ArrayList<String>(myLastCommitMessages);
  }

  public void removeMessage(final String content) {
    myLastCommitMessages.remove(content);
  }


  public PerformInBackgroundOption getUpdateOption() {
    return myUpdateOption;
  }

  public PerformInBackgroundOption getCommitOption() {
    return myCommitOption;
  }

  public PerformInBackgroundOption getEditOption() {
    return myEditOption;
  }

  public PerformInBackgroundOption getCheckoutOption() {
    return myCheckoutOption;
  }

  public PerformInBackgroundOption getAddRemoveOption() {
    return myAddRemoveOption;
  }

  private class UpdateInBackgroundOption implements PerformInBackgroundOption {
    public boolean shouldStartInBackground() {
      return PERFORM_UPDATE_IN_BACKGROUND;
    }

    public void processSentToBackground() {}
  }

  private class CommitInBackgroundOption implements PerformInBackgroundOption {
    public boolean shouldStartInBackground() {
      return PERFORM_COMMIT_IN_BACKGROUND;
    }

    public void processSentToBackground() {}
  }

  private class EditInBackgroundOption implements PerformInBackgroundOption {
    public boolean shouldStartInBackground() {
      return PERFORM_EDIT_IN_BACKGROUND;
    }

    public void processSentToBackground() {
      PERFORM_EDIT_IN_BACKGROUND = true;
    }

  }

  private class CheckoutInBackgroundOption implements PerformInBackgroundOption {
    public boolean shouldStartInBackground() {
      return PERFORM_CHECKOUT_IN_BACKGROUND;
    }

    public void processSentToBackground() {
      PERFORM_CHECKOUT_IN_BACKGROUND = true;
    }

  }

  private class AddRemoveInBackgroundOption implements PerformInBackgroundOption {
    public boolean shouldStartInBackground() {
      return PERFORM_ADD_REMOVE_IN_BACKGROUND;
    }

    public void processSentToBackground() {
      PERFORM_ADD_REMOVE_IN_BACKGROUND = true;
    }

  }

  public String getPatchFileExtension() {
    return DEFAULT_PATCH_EXTENSION;
  }

  public void acceptLastCreatedPatchName(final String string) {
    if (StringUtil.isEmptyOrSpaces(string)) return;
    if (FileUtilRt.extensionEquals(string, DIFF)) {
      DEFAULT_PATCH_EXTENSION = DIFF;
    }
    else if (FileUtilRt.extensionEquals(string, PATCH)) {
      DEFAULT_PATCH_EXTENSION = PATCH;
    }
  }

  public boolean isChangedOnServerEnabled() {
    return CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND;
  }
}
