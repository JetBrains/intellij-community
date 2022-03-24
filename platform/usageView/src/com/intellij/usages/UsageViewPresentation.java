// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

public class UsageViewPresentation {

  private static final Logger LOG = Logger.getInstance(UsageViewPresentation.class);

  private @NlsContexts.TabTitle String myTabText;
  private @NlsSafe String myScopeText = ""; // Default value. to be overwritten in most cases.
  private @NlsSafe String myUsagesString;
  private @NlsSafe String mySearchString;
  private @NlsContexts.ListItem String myTargetsNodeText = UsageViewBundle.message("node.targets");
    // Default value. to be overwritten in most cases.
  private @NlsContexts.ListItem String myNonCodeUsagesString = UsageViewBundle.message("node.non.code.usages");
  private @NlsContexts.ListItem String myCodeUsagesString = UsageViewBundle.message("node.found.usages");
  private boolean myShowReadOnlyStatusAsRed;
  private boolean myShowCancelButton;
  private boolean myOpenInNewTab = true;
  private int myRerunHash;//this value shouldn't be copied and doesn't affect equals/hashcode methods
  private boolean myCodeUsages = true;
  private boolean myUsageTypeFilteringAvailable;

  private @NlsContexts.TabTitle String myTabName;
  private @NlsContexts.TabTitle String myToolwindowTitle;

  private boolean myDetachedMode; // no UI will be shown
  private @NlsContexts.ListItem String myDynamicCodeUsagesString;
  private boolean myMergeDupLinesAvailable = true;
  private boolean myExcludeAvailable = true;
  private Pattern mySearchPattern;
  private Pattern myReplacePattern;
  private boolean myReplaceMode;

  public @NlsContexts.TabTitle String getTabText() {
    return myTabText;
  }

  public void setTabText(@NlsContexts.TabTitle String tabText) {
    myTabText = tabText;
  }

  @NotNull
  @NlsSafe
  public String getScopeText() {
    return myScopeText;
  }

  public void setScopeText(@NotNull @NlsSafe String scopeText) {
    myScopeText = scopeText;
  }

  public boolean isShowReadOnlyStatusAsRed() {
    return myShowReadOnlyStatusAsRed;
  }

  public void setShowReadOnlyStatusAsRed(boolean showReadOnlyStatusAsRed) {
    myShowReadOnlyStatusAsRed = showReadOnlyStatusAsRed;
  }

  /**
   * @deprecated use {@link #setSearchString}
   */
  @Deprecated
  public void setUsagesString(@Nls String usagesString) {
    myUsagesString = usagesString;
  }

  public @Nls(capitalization = Title) @NotNull String getSearchString() {
    String searchString = mySearchString;
    if (searchString != null) {
      return searchString;
    }
    String usagesString = myUsagesString;
    if (usagesString != null) {
      return StringUtil.capitalize(myUsagesString);
    }
    LOG.error("search string must be set");
    return "";
  }

  public void setSearchString(@Nls(capitalization = Title) @NotNull String searchString) {
    mySearchString = searchString;
  }

  @NlsContexts.ListItem
  @Nullable("null means the targets node must not be visible")
  public String getTargetsNodeText() {
    return myTargetsNodeText;
  }

  public void setTargetsNodeText(@NlsContexts.ListItem String targetsNodeText) {
    myTargetsNodeText = targetsNodeText;
  }

  public boolean isShowCancelButton() {
    return myShowCancelButton;
  }

  public void setShowCancelButton(boolean showCancelButton) {
    myShowCancelButton = showCancelButton;
  }

  @NotNull
  public @NlsContexts.ListItem String getNonCodeUsagesString() {
    return myNonCodeUsagesString;
  }

  public void setNonCodeUsagesString(@NotNull @NlsContexts.ListItem String nonCodeUsagesString) {
    myNonCodeUsagesString = nonCodeUsagesString;
  }

  @NotNull
  public @NlsContexts.ListItem String getCodeUsagesString() {
    return myCodeUsagesString;
  }

  public void setCodeUsagesString(@NotNull @NlsContexts.ListItem String codeUsagesString) {
    myCodeUsagesString = codeUsagesString;
  }

  public boolean isOpenInNewTab() {
    return myOpenInNewTab;
  }

  public void setOpenInNewTab(boolean openInNewTab) {
    myOpenInNewTab = openInNewTab;
  }

  public int getRerunHash() {
    return myRerunHash;
  }

  public void setRerunHash(int rerunHash) {
    myRerunHash = rerunHash;
  }

  public boolean isCodeUsages() {
    return myCodeUsages;
  }

  public void setCodeUsages(final boolean codeUsages) {
    myCodeUsages = codeUsages;
  }

  /**
   * @deprecated please avoid using this method, because it leads to string concatenations that are shown in UI
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public @Nls String getUsagesWord() {
    return UsageViewBundle.message("usage.name", 1);
  }

  /**
   * @deprecated no-op
   */
  @Deprecated(forRemoval = true)
  public void setUsagesWord(@NotNull @Nls String usagesWord) {}

  public @NlsContexts.TabTitle String getTabName() {
    return myTabName;
  }

  public void setTabName(final @NlsContexts.TabTitle String tabName) {
    myTabName = tabName;
  }

  public @NlsContexts.TabTitle String getToolwindowTitle() {
    return myToolwindowTitle;
  }

  public void setToolwindowTitle(final @NlsContexts.TabTitle String toolwindowTitle) {
    myToolwindowTitle = toolwindowTitle;
  }

  public boolean isDetachedMode() {
    return myDetachedMode;
  }

  public void setDetachedMode(boolean detachedMode) {
    myDetachedMode = detachedMode;
  }

  public void setDynamicUsagesString(@NlsContexts.ListItem String dynamicCodeUsagesString) {
    myDynamicCodeUsagesString = dynamicCodeUsagesString;
  }

  public @NlsContexts.ListItem String getDynamicCodeUsagesString() {
    return myDynamicCodeUsagesString;
  }

  public boolean isMergeDupLinesAvailable() {
    return myMergeDupLinesAvailable;
  }

  public void setMergeDupLinesAvailable(boolean mergeDupLinesAvailable) {
    myMergeDupLinesAvailable = mergeDupLinesAvailable;
  }

  public boolean isUsageTypeFilteringAvailable() {
    return myCodeUsages || myUsageTypeFilteringAvailable;
  }

  public void setUsageTypeFilteringAvailable(boolean usageTypeFilteringAvailable) {
    myUsageTypeFilteringAvailable = usageTypeFilteringAvailable;
  }

  public boolean isExcludeAvailable() {
    return myExcludeAvailable;
  }

  public void setExcludeAvailable(boolean excludeAvailable) {
    myExcludeAvailable = excludeAvailable;
  }

  public void setSearchPattern(Pattern searchPattern) {
    mySearchPattern = searchPattern;
  }

  public Pattern getSearchPattern() {
    return mySearchPattern;
  }

  public void setReplacePattern(Pattern replacePattern) {
    myReplacePattern = replacePattern;
  }

  public Pattern getReplacePattern() {
    return myReplacePattern;
  }

  public boolean isReplaceMode() {
    return myReplaceMode;
  }

  public void setReplaceMode(boolean replaceMode) {
    myReplaceMode = replaceMode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UsageViewPresentation that = (UsageViewPresentation)o;
    return myCodeUsages == that.myCodeUsages
           && myDetachedMode == that.myDetachedMode
           && myMergeDupLinesAvailable == that.myMergeDupLinesAvailable
           && myOpenInNewTab == that.myOpenInNewTab
           && myShowCancelButton == that.myShowCancelButton
           && myShowReadOnlyStatusAsRed == that.myShowReadOnlyStatusAsRed
           && myUsageTypeFilteringAvailable == that.myUsageTypeFilteringAvailable
           && myExcludeAvailable == that.myExcludeAvailable
           && myReplaceMode == that.myReplaceMode
           && Objects.equals(myCodeUsagesString, that.myCodeUsagesString)
           && Objects.equals(myDynamicCodeUsagesString, that.myDynamicCodeUsagesString)
           && Objects.equals(myNonCodeUsagesString, that.myNonCodeUsagesString)
           && Objects.equals(myScopeText, that.myScopeText)
           && Objects.equals(myTabName, that.myTabName)
           && Objects.equals(myTabText, that.myTabText)
           && Objects.equals(myTargetsNodeText, that.myTargetsNodeText)
           && Objects.equals(myToolwindowTitle, that.myToolwindowTitle)
           && Objects.equals(myUsagesString, that.myUsagesString)
           && Objects.equals(mySearchString, that.mySearchString)
           && arePatternsEqual(mySearchPattern, that.mySearchPattern)
           && arePatternsEqual(myReplacePattern, that.myReplacePattern);
  }

  public static boolean arePatternsEqual(Pattern p1, Pattern p2) {
    if (p1 == null) return p2 == null;
    if (p2 == null) return false;
    return Objects.equals(p1.pattern(), p2.pattern()) && p1.flags() == p2.flags();
  }

  public static int getHashCode(Pattern pattern) {
    if (pattern == null) return 0;
    String s = pattern.pattern();
    return (s != null ? s.hashCode() : 0) * 31 + pattern.flags();
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(
      myTabText,
      myScopeText,
      myUsagesString,
      mySearchString,
      myTargetsNodeText,
      myNonCodeUsagesString,
      myCodeUsagesString,
      myShowReadOnlyStatusAsRed,
      myShowCancelButton,
      myOpenInNewTab,
      myCodeUsages,
      myUsageTypeFilteringAvailable,
      myExcludeAvailable,
      myTabName,
      myToolwindowTitle,
      myDetachedMode,
      myDynamicCodeUsagesString,
      myMergeDupLinesAvailable,
      myReplaceMode
    );
    result = 31 * result + getHashCode(mySearchPattern);
    result = 31 * result + getHashCode(myReplacePattern);
    return result;
  }

  public UsageViewPresentation copy() {
    UsageViewPresentation copyInstance = new UsageViewPresentation();
    copyInstance.myTabText = myTabText;
    copyInstance.myScopeText = myScopeText;
    copyInstance.myUsagesString = myUsagesString;
    copyInstance.mySearchString = mySearchString;
    copyInstance.myTargetsNodeText = myTargetsNodeText;
    copyInstance.myNonCodeUsagesString = myNonCodeUsagesString;
    copyInstance.myCodeUsagesString = myCodeUsagesString;
    copyInstance.myShowReadOnlyStatusAsRed = myShowReadOnlyStatusAsRed;
    copyInstance.myShowCancelButton = myShowCancelButton;
    copyInstance.myOpenInNewTab = myOpenInNewTab;
    copyInstance.myCodeUsages = myCodeUsages;
    copyInstance.myUsageTypeFilteringAvailable = myUsageTypeFilteringAvailable;
    copyInstance.myTabName = myTabName;
    copyInstance.myToolwindowTitle = myToolwindowTitle;
    copyInstance.myDetachedMode = myDetachedMode;
    copyInstance.myDynamicCodeUsagesString = myDynamicCodeUsagesString;
    copyInstance.myMergeDupLinesAvailable = myMergeDupLinesAvailable;
    copyInstance.myExcludeAvailable = myExcludeAvailable;
    copyInstance.mySearchPattern = mySearchPattern;
    copyInstance.myReplacePattern = myReplacePattern;
    copyInstance.myReplaceMode = myReplaceMode;
    return copyInstance;
  }
}
