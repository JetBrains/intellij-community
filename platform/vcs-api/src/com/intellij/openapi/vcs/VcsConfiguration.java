/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@State(
  name = "VcsManagerConfiguration",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public final class VcsConfiguration implements PersistentStateComponent<VcsConfiguration> {
  public final static long ourMaximumFileForBaseRevisionSize = 500 * 1000;

  @NonNls static final String VALUE_ATTR = "value";

  @NonNls public static final String PATCH = "patch";
  @NonNls public static final String DIFF = "diff";

  public boolean OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT = true;
  public boolean CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT = !PlatformUtils.isPyCharm() && !PlatformUtils.isRubyMine();
  public boolean CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT = false;
  public boolean CHECK_NEW_TODO = true;
  public TodoPanelSettings myTodoPanelSettings = new TodoPanelSettings();
  public boolean PERFORM_UPDATE_IN_BACKGROUND = true;
  public boolean PERFORM_COMMIT_IN_BACKGROUND = true;
  public boolean PERFORM_EDIT_IN_BACKGROUND = true;
  public boolean PERFORM_CHECKOUT_IN_BACKGROUND = true;
  public boolean PERFORM_ADD_REMOVE_IN_BACKGROUND = true;
  public boolean PERFORM_ROLLBACK_IN_BACKGROUND = false;
  public volatile boolean CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND = false;
  @OptionTag(tag = "confirmMoveToFailedCommit", nameAttribute = "")
  public VcsShowConfirmationOption.Value MOVE_TO_FAILED_COMMIT_CHANGELIST = VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
  @OptionTag(tag = "confirmRemoveEmptyChangelist", nameAttribute = "")
  public VcsShowConfirmationOption.Value REMOVE_EMPTY_INACTIVE_CHANGELISTS = VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
  public int CHANGED_ON_SERVER_INTERVAL = 60;
  public boolean SHOW_ONLY_CHANGED_IN_SELECTION_DIFF = true;
  public boolean CHECK_COMMIT_MESSAGE_SPELLING = true;
  public String DEFAULT_PATCH_EXTENSION = PATCH;
  // asked only for non-DVCS
  public boolean INCLUDE_TEXT_INTO_SHELF = false;
  public Boolean SHOW_PATCH_IN_EXPLORER = null;
  public boolean SHOW_FILE_HISTORY_DETAILS = true;
  public boolean SHOW_DIRTY_RECURSIVELY = false;
  public boolean LIMIT_HISTORY = true;
  public int MAXIMUM_HISTORY_ROWS = 1000;
  public String UPDATE_FILTER_SCOPE_NAME = null;
  public boolean USE_COMMIT_MESSAGE_MARGIN = false;
  public int COMMIT_MESSAGE_MARGIN_SIZE = 72;
  public boolean WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = false;
  public boolean SHOW_UNVERSIONED_FILES_WHILE_COMMIT = true;
  public boolean LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN = false;

  @AbstractCollection(surroundWithTag = false, elementTag = "path")
  @Tag("ignored-roots")
  public List<String> IGNORED_UNREGISTERED_ROOTS = ContainerUtil.newArrayList();

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

  @Property(surroundWithTag = false)
  @AbstractCollection(elementTag = "MESSAGE", elementValueAttribute = "value", surroundWithTag = false)
  public List<String> myLastCommitMessages = new ArrayList<>();
  public String LAST_COMMIT_MESSAGE = null;
  public boolean MAKE_NEW_CHANGELIST_ACTIVE = false;
  public boolean PRESELECT_EXISTING_CHANGELIST = false;

  public boolean OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = false;
  public boolean CHECK_FILES_UP_TO_DATE_BEFORE_COMMIT = false;

  public boolean REFORMAT_BEFORE_PROJECT_COMMIT = false;
  public boolean REFORMAT_BEFORE_FILE_COMMIT = false;

  public boolean REARRANGE_BEFORE_PROJECT_COMMIT = false;

  public Map<String, ChangeBrowserSettings> CHANGE_BROWSER_SETTINGS = new HashMap<>();

  public boolean UPDATE_GROUP_BY_PACKAGES = false;
  public boolean UPDATE_GROUP_BY_CHANGELIST = false;
  public boolean UPDATE_FILTER_BY_SCOPE = false;
  public boolean SHOW_FILE_HISTORY_AS_TREE = false;
  @Deprecated
  public float FILE_HISTORY_SPLITTER_PROPORTION = 0.6f; // to remove after 2016.3
  private static final int MAX_STORED_MESSAGES = 25;
  @NonNls static final String MESSAGE_ELEMENT_NAME = "MESSAGE";

  private final PerformInBackgroundOption myUpdateOption = new UpdateInBackgroundOption();
  private final PerformInBackgroundOption myCommitOption = new CommitInBackgroundOption();
  private final PerformInBackgroundOption myEditOption = new EditInBackgroundOption();
  private final PerformInBackgroundOption myCheckoutOption = new CheckoutInBackgroundOption();
  private final PerformInBackgroundOption myAddRemoveOption = new AddRemoveInBackgroundOption();

  @Override
  public VcsConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(VcsConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static VcsConfiguration getInstance(@NotNull Project project) {
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
    return new ArrayList<>(myLastCommitMessages);
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
    @Override
    public boolean shouldStartInBackground() {
      return PERFORM_UPDATE_IN_BACKGROUND;
    }

    @Override
    public void processSentToBackground() {}
  }

  private class CommitInBackgroundOption implements PerformInBackgroundOption {
    @Override
    public boolean shouldStartInBackground() {
      return PERFORM_COMMIT_IN_BACKGROUND;
    }

    @Override
    public void processSentToBackground() {}
  }

  private class EditInBackgroundOption implements PerformInBackgroundOption {
    @Override
    public boolean shouldStartInBackground() {
      return PERFORM_EDIT_IN_BACKGROUND;
    }

    @Override
    public void processSentToBackground() {
      PERFORM_EDIT_IN_BACKGROUND = true;
    }

  }

  private class CheckoutInBackgroundOption implements PerformInBackgroundOption {
    @Override
    public boolean shouldStartInBackground() {
      return PERFORM_CHECKOUT_IN_BACKGROUND;
    }

    @Override
    public void processSentToBackground() {
      PERFORM_CHECKOUT_IN_BACKGROUND = true;
    }

  }

  private class AddRemoveInBackgroundOption implements PerformInBackgroundOption {
    @Override
    public boolean shouldStartInBackground() {
      return PERFORM_ADD_REMOVE_IN_BACKGROUND;
    }

    @Override
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

  public void addIgnoredUnregisteredRoots(@NotNull Collection<String> roots) {
    List<String> unregisteredRoots = new ArrayList<>(IGNORED_UNREGISTERED_ROOTS);
    for (String root : roots) {
      if (!unregisteredRoots.contains(root)) {
        unregisteredRoots.add(root);
      }
    }
    IGNORED_UNREGISTERED_ROOTS = unregisteredRoots;
  }

}
