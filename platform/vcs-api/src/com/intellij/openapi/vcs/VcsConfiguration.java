// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.ide.todo.TodoPanelSettings;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.util.PlatformUtils;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@State(
  name = "VcsManagerConfiguration",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public final class VcsConfiguration implements PersistentStateComponent<VcsConfiguration> {
  private static final Logger LOG = Logger.getInstance(VcsConfiguration.class);
  public final static long ourMaximumFileForBaseRevisionSize = 500 * 1000;

  @NonNls public static final String PATCH = "patch";
  @NonNls public static final String DIFF = "diff";

  public boolean OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT = false;
  public boolean CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT =
    !PlatformUtils.isPyCharm() && !PlatformUtils.isRubyMine() && !PlatformUtils.isCLion();
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
  public VcsShowConfirmationOption.Value MOVE_TO_FAILED_COMMIT_CHANGELIST = VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY;
  @OptionTag(tag = "confirmRemoveEmptyChangelist", nameAttribute = "")
  public VcsShowConfirmationOption.Value REMOVE_EMPTY_INACTIVE_CHANGELISTS = VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
  public int CHANGED_ON_SERVER_INTERVAL = 60;
  public boolean SHOW_ONLY_CHANGED_IN_SELECTION_DIFF = true;
  public String DEFAULT_PATCH_EXTENSION = PATCH;
  public boolean USE_CUSTOM_SHELF_PATH = false;
  public String CUSTOM_SHELF_PATH = null;
  public boolean MOVE_SHELVES = false;
  public boolean ADD_EXTERNAL_FILES_SILENTLY = false;
  // asked only for non-DVCS
  public boolean INCLUDE_TEXT_INTO_SHELF = true;
  public Boolean SHOW_PATCH_IN_EXPLORER = null;
  public boolean SHOW_FILE_HISTORY_DETAILS = true;
  public boolean SHOW_DIRTY_RECURSIVELY = false;
  public boolean LIMIT_HISTORY = true;
  public int MAXIMUM_HISTORY_ROWS = 1000;
  public String UPDATE_FILTER_SCOPE_NAME = null;
  public boolean USE_COMMIT_MESSAGE_MARGIN = true;
  public boolean WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = false;
  public boolean SHOW_UNVERSIONED_FILES_WHILE_COMMIT = true;
  public boolean LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN = true;
  public boolean SHELVE_DETAILS_PREVIEW_SHOWN = false;
  public boolean RELOAD_CONTEXT = true;
  public boolean MARK_IGNORED_AS_EXCLUDED = false;

  @XCollection(elementName = "path", propertyElementName = "ignored-roots")
  public List<String> IGNORED_UNREGISTERED_ROOTS = new ArrayList<>();

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

    StandardConfirmation(@NotNull String id) {
      myId = id;
    }

    @NotNull
    private final String myId;

    @NotNull
    public String getId() {
      return myId;
    }
  }

  public boolean FORCE_NON_EMPTY_COMMENT = false;
  public boolean CLEAR_INITIAL_COMMIT_MESSAGE = false;

  @Property(surroundWithTag = false)
  @XCollection(elementName = "MESSAGE")
  public List<String> myLastCommitMessages = new ArrayList<>();
  public String LAST_COMMIT_MESSAGE = null;
  public boolean MAKE_NEW_CHANGELIST_ACTIVE = false;
  public boolean PRESELECT_EXISTING_CHANGELIST = false;

  public boolean OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = false;
  public boolean REFORMAT_BEFORE_PROJECT_COMMIT = false;
  public boolean REARRANGE_BEFORE_PROJECT_COMMIT = false;

  @Transient
  public Map<String, ChangeBrowserSettings> changeBrowserSettings = new THashMap<>();

  public boolean UPDATE_GROUP_BY_PACKAGES = false;
  public boolean UPDATE_GROUP_BY_CHANGELIST = false;
  public boolean UPDATE_FILTER_BY_SCOPE = false;
  public boolean SHOW_FILE_HISTORY_AS_TREE = false;
  public boolean GROUP_MULTIFILE_MERGE_BY_DIRECTORY = false;

  private static final int MAX_STORED_MESSAGES = 25;

  private final PerformInBackgroundOption myUpdateOption = new UpdateInBackgroundOption();
  private final PerformInBackgroundOption myCommitOption = new CommitInBackgroundOption();
  private final PerformInBackgroundOption myEditOption = new EditInBackgroundOption();
  private final PerformInBackgroundOption myCheckoutOption = new CheckoutInBackgroundOption();
  private final PerformInBackgroundOption myAddRemoveOption = new AddRemoveInBackgroundOption();
  private final PerformInBackgroundOption myRollbackOption = new RollbackInBackgroundOption();

  @Override
  public VcsConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull VcsConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static VcsConfiguration getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VcsConfiguration.class);
  }

  public void saveCommitMessage(final String comment) {
    LAST_COMMIT_MESSAGE = comment;
    if (comment == null || comment.length() == 0) return;
    myLastCommitMessages.remove(comment);
    addCommitMessage(comment);
  }

  private void addCommitMessage(@NotNull String comment) {
    if (myLastCommitMessages.size() >= MAX_STORED_MESSAGES) {
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

  public void replaceMessage(@NotNull String oldMessage, @NotNull String newMessage) {
    if (oldMessage.equals(LAST_COMMIT_MESSAGE)) {
      LAST_COMMIT_MESSAGE = newMessage;
    }
    int index = myLastCommitMessages.indexOf(oldMessage);
    if (index >= 0) {
      myLastCommitMessages.remove(index);
      myLastCommitMessages.add(index, newMessage);
    }
    else {
      LOG.debug("Couldn't find message [" + oldMessage + "] in the messages history");
      addCommitMessage(newMessage);
    }
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

  public PerformInBackgroundOption getRollbackOption() {
    return myRollbackOption;
  }

  private class UpdateInBackgroundOption implements PerformInBackgroundOption {
    @Override
    public boolean shouldStartInBackground() {
      return PERFORM_UPDATE_IN_BACKGROUND;
    }
  }

  private class CommitInBackgroundOption implements PerformInBackgroundOption {
    @Override
    public boolean shouldStartInBackground() {
      return PERFORM_COMMIT_IN_BACKGROUND;
    }
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

  private class RollbackInBackgroundOption implements PerformInBackgroundOption {
    @Override
    public boolean shouldStartInBackground() {
      return PERFORM_ROLLBACK_IN_BACKGROUND;
    }

    @Override
    public void processSentToBackground() {
      PERFORM_ROLLBACK_IN_BACKGROUND = true;
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

  public void removeFromIgnoredUnregisteredRoots(@NotNull Collection<String> roots) {
    List<String> unregisteredRoots = new ArrayList<>(IGNORED_UNREGISTERED_ROOTS);
    unregisteredRoots.removeAll(roots);
    IGNORED_UNREGISTERED_ROOTS = unregisteredRoots;
  }

  public boolean isIgnoredUnregisteredRoot(@NotNull String root) {
    return IGNORED_UNREGISTERED_ROOTS.contains(root);
  }
}
