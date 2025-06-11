// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.dvcs.branch.DvcsBranchSettings
import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Property
import git4idea.fetch.GitFetchTagsMode
import git4idea.push.GitPushTagMode
import git4idea.reset.GitResetMode

class GitVcsOptions : BaseState() {
  @get:OptionTag("PATH_TO_GIT")
  var pathToGit: String? by string()

  // The previously entered authors of the commit (up to {@value #PREVIOUS_COMMIT_AUTHORS_LIMIT})
  @get:OptionTag("PREVIOUS_COMMIT_AUTHORS")
  val previousCommitAuthors: MutableList<String> by list()

  // The policy that specifies how files are saved before update or rebase
  @get:OptionTag("SAVE_CHANGES_POLICY")
  var saveChangesPolicy: GitSaveChangesPolicy by enum(GitSaveChangesPolicy.SHELVE)

  @get:OptionTag("UPDATE_TYPE")
  @com.intellij.configurationStore.Property(description = "Update method")
  var updateMethod: UpdateMethod by enum(UpdateMethod.MERGE)

  @com.intellij.configurationStore.Property(description = "gc.auto")
  var gcAuto: String? by string()
  @com.intellij.configurationStore.Property(description = "core.longpaths")
  var coreLongpaths: String? by string()
  @com.intellij.configurationStore.Property(description = "core.untrackedcache")
  var coreUntrackedCache: String? by string()
  @com.intellij.configurationStore.Property(description = "core.fsmonitor")
  var coreFsMonitor: String? by string()
  @com.intellij.configurationStore.Property(description = "feature.manyFiles")
  var featureManyFiles: String? by string()

  @get:OptionTag("PUSH_AUTO_UPDATE")
  var isPushAutoUpdate: Boolean by property(false)
  @get:OptionTag("ROOT_SYNC")
  var rootSync: DvcsSyncSettings.Value by enum(DvcsSyncSettings.Value.NOT_DECIDED)

  @get:OptionTag("RECENT_GIT_ROOT_PATH")
  var recentGitRootPath: String? by string()
  @get:OptionTag("RECENT_BRANCH_BY_REPOSITORY")
  val recentBranchByRepository: MutableMap<String, String> by map()
  @get:OptionTag("RECENT_COMMON_BRANCH")
  var recentCommonBranch: String? by string()
  @get:OptionTag("SHOW_RECENT_BRANCHES")
  var showRecentBranches: Boolean by property(true)
  @get:OptionTag("SHOW_TAGS")
  var showTags: Boolean by property(true)

  @get:OptionTag("FILTER_BY_ACTION_IN_POPUP")
  var filterByActionInPopup: Boolean by property(true)
  @get:OptionTag("FILTER_BY_REPOSITORY_IN_POPUP")
  var filterByRepositoryInPopup: Boolean by property(true)

  @get:OptionTag("WARN_ABOUT_CRLF")
  var warnAboutCrlf: Boolean by property(true)
  @get:OptionTag("WARN_ABOUT_DETACHED_HEAD")
  var isWarnAboutDetachedHead: Boolean by property(true)
  @get:OptionTag("WARN_ABOUT_LARGE_FILES")
  var isWarnAboutLargeFiles: Boolean by property(true)
  @get:OptionTag("WARN_ABOUT_LARGE_FILES_LIMIT_MB")
  var warnAboutLargeFilesLimitMb: Int by property(50)
  @get:OptionTag("WARN_ABOUT_BAD_FILE_NAMES")
  var isWarnAboutBadFileNames: Boolean by property(true)

  @get:OptionTag("RESET_MODE")
  var resetMode: GitResetMode? by enum<GitResetMode>()
  @get:OptionTag("PUSH_TAGS")
  var pushTags: GitPushTagMode? by property()

  @get:OptionTag("FETCH_TAGS_MODE")
  var fetchTagsMode: GitFetchTagsMode by enum<GitFetchTagsMode>(GitFetchTagsMode.DEFAULT)

  @get:OptionTag("INCOMING_CHECK_STRATEGY")
  var incomingCheckStrategy: GitIncomingCheckStrategy by enum(GitIncomingCheckStrategy.Auto)

  @get:OptionTag("SIGN_OFF_COMMIT")
  var isSignOffCommit: Boolean by property(false)
  @get:OptionTag("SET_USER_NAME_GLOBALLY")
  var isSetUserNameGlobally: Boolean by property(true)
  @get:OptionTag("SWAP_SIDES_IN_COMPARE_BRANCHES")
  var isSwapSidesInCompareBranches: Boolean by property(false)
  @get:OptionTag("UPDATE_BRANCHES_INFO")
  var isUpdateBranchesInfo: Boolean by property(true)
  @get:OptionTag("PREVIEW_PUSH_ON_COMMIT_AND_PUSH")
  var isPreviewPushOnCommitAndPush: Boolean by property(true)
  @get:OptionTag("PREVIEW_PUSH_PROTECTED_ONLY")
  var isPreviewPushProtectedOnly: Boolean by property(false)
  @get:OptionTag("COMMIT_RENAMES_SEPARATELY")
  var isCommitRenamesSeparately: Boolean by property(true)
  @get:OptionTag("ADD_SUFFIX_TO_CHERRY_PICKS_OF_PUBLISHED_COMMITS")
  var isAddSuffixToCherryPicksOfPublishedCommits: Boolean by property(true)

  @get:OptionTag("BRANCH_SETTINGS")
  @get:Property(surroundWithTag = false, flat = true)
  var branchSettings: DvcsBranchSettings by property(DvcsBranchSettings())
}
