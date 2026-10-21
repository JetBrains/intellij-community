// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.whatsNew;

import com.intellij.idea.AppMode;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class WhatsNewUtil {
  public static final String PLACE = "WhatsNew";

  public static boolean isWhatsNewEnabled() {
    return (
      !AppMode.isRemoteDevHost() &&
      UpdateStrategyCustomization.getInstance().getShowWhatIsNewPageAfterUpdate() &&
      ExternalProductResourceUrls.getInstance().getWhatIsNewPageUrl() != null
    );
  }
}
