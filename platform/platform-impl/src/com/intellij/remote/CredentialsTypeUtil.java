// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.remote.ext.CredentialsLanguageContribution;
import com.intellij.remote.ext.CredentialsManager;
import com.intellij.remote.ui.CredentialsEditorProvider;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class CredentialsTypeUtil {
  private CredentialsTypeUtil() {
  }

  public static boolean isCredentialsTypeSupportedForLanguage(@NotNull CredentialsType<?> credentialsType,
                                                              @NotNull Class<?> languageContributionMarkerClass) {
    // TODO add language contributors for Python and Node JS
    for (CredentialsType<?> type : CredentialsManager.getInstance().getAllTypes()) {
      if (credentialsType.equals(type)) {
        CredentialsEditorProvider editorProvider = ObjectUtils.tryCast(type, CredentialsEditorProvider.class);
        if (editorProvider != null) {
          List<CredentialsLanguageContribution> contributions = getContributions(languageContributionMarkerClass);
          if (!contributions.isEmpty()) {
            for (CredentialsLanguageContribution contribution : contributions) {
              if (contribution.getType() == type && editorProvider.isAvailable(contribution)) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  public static @NotNull <T> List<CredentialsLanguageContribution> getContributions(@NotNull Class<T> languageContributionMarkerInterface) {
    return ContainerUtil.filter(CredentialsLanguageContribution.EP_NAME.getExtensions(),
                                FilteringIterator.instanceOf(languageContributionMarkerInterface));
  }
}
