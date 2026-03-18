package com.intellij.driver.sdk.ui.settings

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher

/**
 * Programmatic settings update sometimes requires additional action in IDE to be done in order to launch actual settings update.
 * For example, you can set close button for tab to be located on the left (while default - on the right) by calling
 * `updateSettings { closeTabButtonOnTheRight = false }` and then calling `fireUISettingsChanged()` (see [UiSettings.fireUISettingsChanged])
 *
 * If check on button location will happen immediately, changes can not be seen, so better approach in this particular case would be:
 * - Settings change
 * - Some other change (for example - click on tab)
 * - Check button presence
 */
@Remote("com.intellij.ide.ui.UISettings")
interface UiSettings {
  var state: UiSettingsState
  fun fireUISettingsChanged()
}

@Remote("com.intellij.ide.ui.UISettingsState")
interface UiSettingsState {
  // Editor Tabs
  var editorTabLimit: Int
  var editorTabPlacement: Int
  var scrollTabLayoutInEditor: Boolean
  var hideTabsIfNeeded: Boolean
  var showPinnedTabsInASeparateRow: Boolean
  var showCloseButton: Boolean
  var closeTabButtonOnTheRight: Boolean
  var showFileIconInTabs: Boolean
  var showTabsTooltips: Boolean
  var showDirectoryForNonUniqueFilenames: Boolean
  var hideKnownExtensionInTabs: Boolean
  var markModifiedTabsWithAsterisk: Boolean
  var sortTabsAlphabetically: Boolean
  var alwaysKeepTabsAlphabeticallySorted: Boolean
  var openTabsAtTheEnd: Boolean
  var reuseNotModifiedTabs: Boolean
  var openTabsInMainWindow: Boolean
  var openInPreviewTabIfPossible: Boolean
  var closeNonModifiedFilesFirst: Boolean
  var activeMruEditorOnClose: Boolean
  var activeRightEditorOnClose: Boolean
  var dndWithPressedAltOnly: Boolean

  // Fonts & Display
  var fontFace: String?
  var fontSize: Int
  var fontScale: Float

  // Recent Files & History
  var recentFilesLimit: Int
  var recentLocationsLimit: Int
  var consoleCommandHistoryLimit: Int
  var overrideConsoleCycleBufferSize: Boolean
  var consoleCycleBufferSizeKb: Int

  // Tool Windows
  var showToolWindowsNumbers: Boolean
  var showToolWindowsNames: Boolean
  var toolWindowLeftSideCustomWidth: Int
  var toolWindowRightSideCustomWidth: Int
  var hideToolStripes: Boolean
  var wideScreenSupport: Boolean
  var rememberSizeForEachToolWindowOldUI: Boolean
  var rememberSizeForEachToolWindowNewUI: Boolean
  var leftHorizontalSplit: Boolean
  var rightHorizontalSplit: Boolean
  var differentToolwindowBackground: Boolean

  // UI Elements
  var showEditorToolTip: Boolean
  var showWriteThreadIndicator: Boolean
  var allowMergeButtons: Boolean
  var showMainToolbar: Boolean
  var showNewMainToolbar: Boolean
  var showStatusBar: Boolean
  var showMainMenu: Boolean
  var showNavigationBar: Boolean
  var navigationBarLocation: String
  var showMembersInNavigationBar: Boolean

  // Tree & Lookup
  var showTreeIndentGuides: Boolean
  var compactTreeIndents: Boolean
  var expandNodesWithSingleClick: Boolean
  var maxLookupWidth: Int
  var maxLookupListHeight: Int
  var sortLookupElementsLexicographically: Boolean

  // Appearance
  var uiDensity: String
  var differentiateProjects: Boolean
  var ideAAType: String
  var editorAAType: String
  var colorBlindness: String?
  var useContrastScrollBars: Boolean
  var moveMouseOnDefaultButton: Boolean
  var enableAlphaMode: Boolean
  var alphaModeDelay: Int
  var alphaModeRatio: Float
  var showIconsInMenus: Boolean
  var keepPopupsForToggles: Boolean
  var disableMnemonics: Boolean
  var disableMnemonicsInControls: Boolean
  var useSmallLabelsOnTabs: Boolean

  // Menu & Window
  var separateMainMenu: Boolean
  var mainMenuDisplayMode: String?
  var defaultAutoScrollToSource: Boolean
  var presentationMode: Boolean
  var presentationModeFontSize: Int
  var fullPathsInWindowHeader: Boolean
  var mergeMainMenuWithWindowTitle: Boolean

  // Scrolling & Animation
  var smoothScrolling: Boolean
  var navigateToPreview: Boolean
  var animatedScrolling: Boolean
  var animatedScrollingDuration: Int
  var animatedScrollingCurvePoints: Int

  // Advanced
  var mergeEqualStackTraces: Boolean
  var showVirtualThreadContainers: Boolean
  var showOnlyPlatformThreads: Boolean
  var sortBookmarks: Boolean
  var pinFindInPath: Boolean
  var showInplaceComments: Boolean
  var showInplaceCommentsInternal: Boolean
  var showVisualFormattingLayer: Boolean
  var showBreakpointsOverLineNumbers: Boolean
  var showPreviewInSearchEverywhere: Boolean
  var showProgressesInEditor: Boolean
  var useSimplifiedSplashImage: Boolean
}

fun Driver.updateUiSettings(settingsToUpdate: UiSettingsState.() -> Unit) {
  val uiSettings = service(UiSettings::class)
  uiSettings.state.apply { settingsToUpdate() }
  withContext(OnDispatcher.EDT) {
    uiSettings.fireUISettingsChanged()
  }
}

fun Driver.getCurrentUiSettings(): UiSettingsState = service(UiSettings::class).state