// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.ide.todo.TodoPanelSettings;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Consumer;

@Service(Service.Level.PROJECT)
@State(name = "VcsManagerConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class VcsConfiguration implements PersistentStateComponent<VcsConfiguration> {
  private static final Logger LOG = Logger.getInstance(VcsConfiguration.class);
  public final static long ourMaximumFileForBaseRevisionSize = 500 * 1000;

  @NonNls public static final String PATCH = "patch";
  @NonNls public static final String DIFF = "diff";

  public boolean CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT =
    !PlatformUtils.isPyCharm() && !PlatformUtils.isRubyMine() && !PlatformUtils.isCLion();
  public String CODE_SMELLS_PROFILE = null;
  public boolean CODE_SMELLS_PROFILE_LOCAL = false;
  public String CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT_PROFILE = null;
  public boolean CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT = false;
  public boolean CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT_LOCAL = false;
  public boolean CHECK_NEW_TODO = true;
  public TodoPanelSettings myTodoPanelSettings = new TodoPanelSettings();
  public volatile boolean CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND = false;
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
  @NlsSafe public String UPDATE_FILTER_SCOPE_NAME = null;
  public boolean USE_COMMIT_MESSAGE_MARGIN = true;
  public boolean WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = false;
  public boolean LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN = true;
  public boolean SHELVE_DETAILS_PREVIEW_SHOWN = false;
  public boolean RELOAD_CONTEXT = true;
  public boolean MARK_IGNORED_AS_EXCLUDED = false;

  @XCollection(elementName = "path", propertyElementName = "ignored-roots")
  public List<String> IGNORED_UNREGISTERED_ROOTS = new ArrayList<>();

  public enum StandardOption {
    ADD("Add", "vcs.command.name.add"),
    REMOVE("Remove", "vcs.command.name.remove"),
    EDIT("Edit", "vcs.command.name.edit"),
    CHECKOUT("Checkout", "vcs.command.name.checkout"),
    STATUS("Status", "vcs.command.name.status"),
    UPDATE("Update", "vcs.command.name.update");

    StandardOption(@NonNls @NotNull String id,
                   @NonNls @PropertyKey(resourceBundle = VcsBundle.BUNDLE) String key) {
      myId = id;
      myKey = key;
    }

    private final String myId;
    private final String myKey;

    @NonNls
    public String getId() {
      return myId;
    }

    @Nls
    public String getDisplayName() {
      return VcsBundle.message(myKey);
    }
  }

  public enum StandardConfirmation {
    ADD("Add", "vcs.command.name.add"),
    REMOVE("Remove", "vcs.command.name.remove");

    StandardConfirmation(@NonNls @NotNull String id,
                         @NotNull @PropertyKey(resourceBundle = VcsBundle.BUNDLE) String key) {
      myId = id;
      myKey = key;
    }

    @NotNull
    private final String myId;
    private final String myKey;

    @NotNull
    public String getId() {
      return myId;
    }

    @Nls
    public String getDisplayName() {
      return VcsBundle.message(myKey);
    }
  }

  public boolean CLEAR_INITIAL_COMMIT_MESSAGE = false;

  @Property(surroundWithTag = false)
  @XCollection(elementName = "MESSAGE")
  public List<String> myLastCommitMessages = new ArrayList<>();
  public @Nullable String LAST_COMMIT_MESSAGE = null;
  public @NotNull String LAST_CHUNK_COMMIT_MESSAGE = "";
  public boolean MAKE_NEW_CHANGELIST_ACTIVE = false;
  public boolean PRESELECT_EXISTING_CHANGELIST = false;

  public boolean OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = false;
  public boolean REFORMAT_BEFORE_PROJECT_COMMIT = false;
  public boolean REARRANGE_BEFORE_PROJECT_COMMIT = false;

  @Transient
  public Map<String, ChangeBrowserSettings> changeBrowserSettings = new HashMap<>();

  public boolean UPDATE_GROUP_BY_PACKAGES = false;
  public boolean UPDATE_GROUP_BY_CHANGELIST = false;
  public boolean UPDATE_FILTER_BY_SCOPE = false;
  public boolean SHOW_FILE_HISTORY_AS_TREE = false;
  public boolean GROUP_MULTIFILE_MERGE_BY_DIRECTORY = false;

  public boolean NON_MODAL_COMMIT_POSTPONE_SLOW_CHECKS = true;

  private static final int MAX_STORED_MESSAGES = 25;

  @Override
  public VcsConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull VcsConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static VcsConfiguration getInstance(@NotNull Project project) {
    return project.getService(VcsConfiguration.class);
  }

  public void saveCommitMessage(final String comment) {
    LAST_COMMIT_MESSAGE = comment;
    if (comment == null || comment.isBlank()) return;

    updateRecentMessages(recentMessages -> {
      recentMessages.remove(comment);
      addCommitMessage(recentMessages, comment);
    });
  }

  public void saveTempChunkCommitMessage(@NotNull final String comment) {
    LAST_CHUNK_COMMIT_MESSAGE = comment;
  }

  public @NotNull String getTempChunkCommitMessage() {
    return LAST_CHUNK_COMMIT_MESSAGE;
  }

  private static void addCommitMessage(@NotNull List<? super String> recentMessages, @NotNull String comment) {
    if (recentMessages.size() >= MAX_STORED_MESSAGES) {
      recentMessages.remove(0);
    }
    recentMessages.add(comment);
  }

  public String getLastNonEmptyCommitMessage() {
    return ContainerUtil.getLastItem(myLastCommitMessages);
  }

  @NotNull
  @CalledInAny
  public ArrayList<String> getRecentMessages() {
    return new ArrayList<>(myLastCommitMessages);
  }

  public void setRecentMessages(List<String> messages) {
    myLastCommitMessages = new ArrayList<>(messages);
  }

  private void updateRecentMessages(@NotNull Consumer<? super ArrayList<String>> modification) {
    ArrayList<String> messages = getRecentMessages();
    modification.accept(messages);
    setRecentMessages(messages);
  }

  public void replaceMessage(@NotNull String oldMessage, @NotNull String newMessage) {
    if (oldMessage.equals(LAST_COMMIT_MESSAGE)) {
      LAST_COMMIT_MESSAGE = newMessage;
    }

    updateRecentMessages(recentMessages -> {
      int index = recentMessages.indexOf(oldMessage);
      if (index >= 0) {
        recentMessages.remove(index);
        recentMessages.add(index, newMessage);
      }
      else {
        LOG.debug("Couldn't find message [" + oldMessage + "] in the messages history");
        addCommitMessage(recentMessages, newMessage);
      }
    });
  }

  /**
   * @deprecated Always start progress in background
   */
  @Deprecated
  public PerformInBackgroundOption getUpdateOption() {
    return PerformInBackgroundOption.ALWAYS_BACKGROUND;
  }

  public String getPatchFileExtension() {
    return DEFAULT_PATCH_EXTENSION;
  }

  public void acceptLastCreatedPatchName(@Nullable String string) {
    if (StringUtil.isEmptyOrSpaces(string)) {
      return;
    }
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
