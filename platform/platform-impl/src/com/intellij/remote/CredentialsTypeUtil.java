// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.remote.ext.CredentialsLanguageContribution;
import com.intellij.remote.ext.CredentialsManager;
import com.intellij.remote.ext.CredentialsTypeEx;
import com.intellij.remote.ui.CredentialsEditorProvider;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CredentialsTypeUtil {
  public static final ImmutableSet<CredentialsType> BUILTIN_CREDENTIALS_TYPE = ImmutableSet.of(CredentialsType.SSH_HOST,
                                                                                               CredentialsType.VAGRANT,
                                                                                               CredentialsType.WEB_DEPLOYMENT);

  private CredentialsTypeUtil() {
  }


  public static void showCannotLoadCredentialsErrorDialog(@Nullable Project project,
                                                          @NotNull String interpreterId,
                                                          @NotNull CredentialsType connectionType) {
    final String message = connectionType instanceof CredentialsTypeEx ? getCannotLoadCredentialsTypeMessage(connectionType) :
                           getCannotLoadCredentialsTypeMessage(interpreterId);
    UIUtil.invokeLaterIfNeeded(() -> Messages.showErrorDialog(project, message, "Failed Loading Interpreter"));
  }

  @NotNull
  public static String getCannotLoadCredentialsTypeMessage(@NotNull final CredentialsType type) {
    return getCannotLoadCredentialsTypeMessage(type.getName());
  }

  @NotNull
  private static String getCannotLoadCredentialsTypeMessage(@NotNull final String interpreterId) {
    return "Cannot load " + interpreterId + " interpreter. Please make sure corresponding plugin is enabled.";
  }

  public static boolean isCredentialsTypeSupportedForLanguage(@NotNull CredentialsType credentialsType,
                                                              @NotNull Class<?> languageContributionMarkerClass) {
    if (BUILTIN_CREDENTIALS_TYPE.contains(credentialsType)) {
      return true;
    }
    for (CredentialsTypeEx typeEx : CredentialsManager.getInstance().getExTypes()) {
      if (credentialsType.equals(typeEx)) {
        CredentialsEditorProvider editorProvider = ObjectUtils.tryCast(typeEx, CredentialsEditorProvider.class);
        if (editorProvider != null) {
          List<CredentialsLanguageContribution> contributions = getContributions(languageContributionMarkerClass);
          if (!contributions.isEmpty()) {
            for (CredentialsLanguageContribution contribution : contributions) {
              if (contribution.getType() == typeEx && editorProvider.isAvailable(contribution)) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  @NotNull
  public static <T> List<CredentialsLanguageContribution> getContributions(@NotNull Class<T> languageContributionMarkerInterface) {
    return ContainerUtil.filter(CredentialsLanguageContribution.EP_NAME.getExtensions(),
                                FilteringIterator.instanceOf(languageContributionMarkerInterface));
  }
}
