// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

public final class SdkAppearanceServiceImpl extends SdkAppearanceService {
  @Override
  @NotNull
  public CellAppearanceEx forNullSdk(boolean selected) {
    return FileAppearanceService.getInstance().forInvalidUrl(ProjectBundle.message("sdk.missing.item"));
  }

  @NotNull
  @Override
  public CellAppearanceEx forSdk(@Nullable Sdk sdk, boolean isInComboBox, boolean selected, boolean showVersion) {
    if (sdk == null) {
      return forNullSdk(selected);
    }

    String name = sdk.getName();
    SdkType sdkType = (SdkType)sdk.getSdkType();
    boolean hasValidPath = sdkType.sdkHasValidPath(sdk);
    String versionString = showVersion ? sdk.getVersionString() : null;
    return forSdk(sdkType, name, versionString, hasValidPath, isInComboBox, selected);
  }

  @Override
  @NotNull
  public CellAppearanceEx forSdk(@NotNull SdkTypeId sdkType,
                                 @NotNull String name,
                                 @Nullable String versionString,
                                 boolean hasValidPath,
                                 boolean isInComboBox,
                                 boolean selected) {
    CompositeAppearance appearance = new CompositeAppearance();
    if (sdkType instanceof SdkType) {
      appearance.setIcon(((SdkType)sdkType).getIcon());
    }

    SimpleTextAttributes attributes = getTextAttributes(hasValidPath, selected);
    CompositeAppearance.DequeEnd ending = appearance.getEnding();
    ending.addText(StringUtil.shortenTextWithEllipsis(name, 50, 0), attributes);

    if (versionString != null && !versionString.equals(name) && !StringUtil.isEmptyOrSpaces(versionString)) {
      SimpleTextAttributes textAttributes = isInComboBox && !selected
                                            ? SimpleTextAttributes.SYNTHETIC_ATTRIBUTES
                                            : SystemInfo.isMac && selected
                                              ? new SimpleTextAttributes(STYLE_PLAIN, JBColor.WHITE)
                                              : GRAY_ATTRIBUTES;

      @NlsSafe String shortVersion = StringUtil.shortenTextWithEllipsis(versionString, 30, 0);
      ending.addComment(shortVersion, textAttributes);
    }

    return ending.getAppearance();
  }

  private static SimpleTextAttributes getTextAttributes(final boolean valid, final boolean selected) {
    if (!valid) {
      return SimpleTextAttributes.ERROR_ATTRIBUTES;
    }
    else if (selected && !(SystemInfoRt.isWindows && UIManager.getLookAndFeel().getName().contains("Windows"))) {
      return SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES;
    }
    else {
      return SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
    }
  }
}
