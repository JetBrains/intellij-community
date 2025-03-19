// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes.metadata;

import com.intellij.ide.ui.UIThemeMetadata;
import com.intellij.ide.ui.UIThemeMetadataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointAdapter;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public final class UIThemeMetadataService {

  public static final ExtensionPointName<UIThemeMetadataProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.themeMetadataProvider");

  private final Map<UIThemeMetadata, @Unmodifiable Map<String, UIThemeMetadata.UIKeyMetadata>> myCache = new HashMap<>();

  public static UIThemeMetadataService getInstance() {
    return ApplicationManager.getApplication().getService(UIThemeMetadataService.class);
  }

  UIThemeMetadataService() {
    loadMetadata();
    EP_NAME.addExtensionPointListener(new ExtensionPointAdapter<>() {
      @Override
      public void extensionListChanged() {
        myCache.clear();
        loadMetadata();
      }
    }, null);
  }

  private void loadMetadata() {
    final List<UIThemeMetadata> themeMetadata = ContainerUtil.mapNotNull(EP_NAME.getExtensionList(), UIThemeMetadataProvider::loadMetadata);
    for (UIThemeMetadata metadata : themeMetadata) {
      myCache.put(metadata, ContainerUtil.newMapFromValues(metadata.getUiKeyMetadata().iterator(), o -> o.getKey()));
    }
  }

  public boolean processAllKeys(PairProcessor<? super UIThemeMetadata, ? super UIThemeMetadata.UIKeyMetadata> processor) {
    for (Map.Entry<UIThemeMetadata, Map<String, UIThemeMetadata.UIKeyMetadata>> entry : myCache.entrySet()) {
      for (Map.Entry<String, UIThemeMetadata.UIKeyMetadata> uiKeyMetadataEntry : entry.getValue().entrySet()) {
        if (!processor.process(entry.getKey(), uiKeyMetadataEntry.getValue())) return false;
      }
    }
    return true;
  }

  public @Nullable Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> findByKey(String key) {
    var baseKey = key;
    baseKey = StringUtil.trimEnd(baseKey, ".compact");
    baseKey = StringUtil.trimEnd(baseKey, ".os.mac");
    baseKey = StringUtil.trimEnd(baseKey, ".os.windows");
    baseKey = StringUtil.trimEnd(baseKey, ".os.linux");
    baseKey = StringUtil.trimEnd(baseKey, ".os.default");
    for (Map.Entry<UIThemeMetadata, Map<String, UIThemeMetadata.UIKeyMetadata>> entry : myCache.entrySet()) {
      final UIThemeMetadata.UIKeyMetadata byKey = entry.getValue().get(baseKey);
      if (byKey != null) {
        return Pair.pair(entry.getKey(), byKey);
      }
    }
    return null;
  }
}
