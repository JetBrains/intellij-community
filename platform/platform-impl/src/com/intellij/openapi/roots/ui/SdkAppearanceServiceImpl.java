// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SdkAppearanceServiceImpl extends SdkAppearanceService {

  @NotNull
  @Override
  public CellAppearanceEx forSdk(@Nullable Sdk sdk, boolean isInComboBox, boolean selected, boolean showVersion) {
    if (sdk == null) {
      return FileAppearanceService.getInstance().forInvalidUrl(ProjectBundle.message("sdk.missing.item"));
    }

    String name = sdk.getName();
    CompositeAppearance appearance = new CompositeAppearance();
    SdkType sdkType = (SdkType)sdk.getSdkType();
    appearance.setIcon(sdkType.getIcon());
    SimpleTextAttributes attributes = getTextAttributes(sdkType.sdkHasValidPath(sdk), selected);
    CompositeAppearance.DequeEnd ending = appearance.getEnding();
    ending.addText(name, attributes);

    if (showVersion) {
      String versionString = sdk.getVersionString();
      if (versionString != null && !versionString.equals(name)) {
        SimpleTextAttributes textAttributes = isInComboBox && !selected ? SimpleTextAttributes.SYNTHETIC_ATTRIBUTES :
                                              SystemInfo.isMac && selected ? new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                                                                                      Color.WHITE) : SimpleTextAttributes.GRAY_ATTRIBUTES;
        ending.addComment(versionString, textAttributes);
      }
    }

    return ending.getAppearance();
  }

  private static SimpleTextAttributes getTextAttributes(final boolean valid, final boolean selected) {
    if (!valid) {
      return SimpleTextAttributes.ERROR_ATTRIBUTES;
    }
    else if (selected && !(SystemInfo.isWinVistaOrNewer && UIManager.getLookAndFeel().getName().contains("Windows"))) {
      return SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES;
    }
    else {
      return SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
    }
  }
}
