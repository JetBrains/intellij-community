// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes.metadata;

import com.intellij.ide.ui.UIThemeMetadata;
import com.intellij.ide.ui.UIThemeMetadataProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public class UIThemeMetadataService {

  private static final ExtensionPointName<UIThemeMetadataProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.themeMetadataProvider");

  private final List<UIThemeMetadata> myUIThemeMetadata;

  public static UIThemeMetadataService getInstance() {
    return ServiceManager.getService(UIThemeMetadataService.class);
  }

  public UIThemeMetadataService() {
    myUIThemeMetadata = ContainerUtil.mapNotNull(EP_NAME.getExtensionList(), UIThemeMetadataProvider::loadMetadata);
  }

  public boolean processAllKeys(PairProcessor<? super UIThemeMetadata, ? super UIThemeMetadata.UIKeyMetadata> processor) {
    for (UIThemeMetadata metadata : myUIThemeMetadata) {
      for (UIThemeMetadata.UIKeyMetadata keyMetadata : metadata.getUiKeyMetadata()) {
        if (!processor.process(metadata, keyMetadata)) return false;
      }
    }
    return true;
  }
}
