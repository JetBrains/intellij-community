// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.dvcs.branch.DvcsBranchSettings
import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Property
import git4idea.push.GitPushTagMode
import git4idea.reset.GitResetMode

class GitVcsOptions : BaseState() {
  @get:OptionTag("PATH_TO_GIT")
  var pathToGit by string()

  // The previously entered authors of the commit (up to {@value #PREVIOUS_COMMIT_AUTHORS_LIMIT})
  @get:OptionTag("PREVIOUS_COMMIT_AUTHORS")
  val previousCommitAuthors by list<String>()

  // The policy that specifies how files are saved before update or rebase
  @get:OptionTag("SAVE_CHANGES_POLICY")
  var saveChangesPolicy by enum(GitSaveChangesPolicy.SHELVE)

  @get:OptionTag("UPDATE_TYPE")
  @com.intellij.configurationStore.Property(description = "Update method")
  var updateMethod by enum(UpdateMethod.MERGE)

  @com.intellij.configurationStore.Property(description = "gc.auto")
  var gcAuto by string()
  @com.intellij.configurationStore.Property(description = "core.longpaths")
  var coreLongpaths by string()
  @com.intellij.configurationStore.Property(description = "core.untrackedcache")
  var coreUntrackedCache by string()
  @com.intellij.configurationStore.Property(description = "core.fsmonitor")
  var coreFsMonitor by string()
  @com.intellij.configurationStore.Property(description = "feature.manyFiles")
  var featureManyFiles by string()

  @get:OptionTag("PUSH_AUTO_UPDATE")
  var isPushAutoUpdate by property(false)
  @get:OptionTag("ROOT_SYNC")
  var rootSync by enum(DvcsSyncSettings.Value.NOT_DECIDED)

  @get:OptionTag("RECENT_GIT_ROOT_PATH")
  var recentGitRootPath by string()
  @get:OptionTag("RECENT_BRANCH_BY_REPOSITORY")
  val recentBranchByRepository by map<String, String>()
  @get:OptionTag("RECENT_COMMON_BRANCH")
  var recentCommonBranch by string()
  @get:OptionTag("SHOW_RECENT_BRANCHES")
  var showRecentBranches by property(true)
  @get:OptionTag("SHOW_TAGS")
  var showTags by property(true)

  @get:OptionTag("FILTER_BY_ACTION_IN_POPUP")
  var filterByActionInPopup by property(true)
  @get:OptionTag("FILTER_BY_REPOSITORY_IN_POPUP")
  var filterByRepositoryInPopup by property(true)

  @get:OptionTag("WARN_ABOUT_CRLF")
  var warnAboutCrlf by property(true)
  @get:OptionTag("WARN_ABOUT_DETACHED_HEAD")
  var isWarnAboutDetachedHead by property(true)
  @get:OptionTag("WARN_ABOUT_LARGE_FILES")
  var isWarnAboutLargeFiles by property(true)
  @get:OptionTag("WARN_ABOUT_LARGE_FILES_LIMIT_MB")
  var warnAboutLargeFilesLimitMb by property(50)
  @get:OptionTag("WARN_ABOUT_BAD_FILE_NAMES")
  var isWarnAboutBadFileNames by property(true)

  @get:OptionTag("RESET_MODE")
  var resetMode by enum<GitResetMode>()
  @get:OptionTag("PUSH_TAGS")
  var pushTags by property<GitPushTagMode>()

  @get:OptionTag("INCOMING_CHECK_STRATEGY")
  var incomingCheckStrategy by enum(GitIncomingCheckStrategy.Auto)

  @get:OptionTag("SIGN_OFF_COMMIT")
  var isSignOffCommit by property(false)
  @get:OptionTag("SET_USER_NAME_GLOBALLY")
  var isSetUserNameGlobally by property(true)
  @get:OptionTag("SWAP_SIDES_IN_COMPARE_BRANCHES")
  var isSwapSidesInCompareBranches by property(false)
  @get:OptionTag("UPDATE_BRANCHES_INFO")
  var isUpdateBranchesInfo by property(true)
  @get:OptionTag("PREVIEW_PUSH_ON_COMMIT_AND_PUSH")
  var isPreviewPushOnCommitAndPush by property(true)
  @get:OptionTag("PREVIEW_PUSH_PROTECTED_ONLY")
  var isPreviewPushProtectedOnly by property(false)
  @get:OptionTag("COMMIT_RENAMES_SEPARATELY")
  var isCommitRenamesSeparately by property(true)
  @get:OptionTag("ADD_SUFFIX_TO_CHERRY_PICKS_OF_PUBLISHED_COMMITS")
  var isAddSuffixToCherryPicksOfPublishedCommits by property(true)

  @get:OptionTag("BRANCH_SETTINGS")
  @get:Property(surroundWithTag = false, flat = true)
  var branchSettings by property(DvcsBranchSettings())
}
