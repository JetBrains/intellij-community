// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.JBProtocolCommand;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.CheckoutProviderEx;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.AppIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @author Konstantin Bulenkov
 */
final class JBProtocolCheckoutCommand extends JBProtocolCommand {
  private static final String REPOSITORY_NAME_KEY = "checkout.repo";

  JBProtocolCheckoutCommand() {
    super("checkout"); //NON-NLS
  }


  @Override
  public @NotNull Future<@Nullable @DialogMessage String> perform(@Nullable String target, @NotNull Map<String, String> parameters, @Nullable String fragment) {
    String repository = parameters.get(REPOSITORY_NAME_KEY);
    if (repository == null || repository.isBlank()) return CompletableFuture.completedFuture(IdeBundle.message("ide.protocol.parameter.missing", REPOSITORY_NAME_KEY));

    CheckoutProviderEx provider = (CheckoutProviderEx)CheckoutProvider.EXTENSION_POINT_NAME.findFirstSafe(
      it -> it instanceof CheckoutProviderEx && ((CheckoutProviderEx)it).getVcsId().equals(target));
    if (provider == null) return CompletableFuture.completedFuture(VcsBundle.message("jb.protocol.no.provider", target));

    Project project = ProjectManager.getInstance().getDefaultProject();
    CheckoutProvider.Listener listener = ProjectLevelVcsManager.getInstance(project).getCompositeCheckoutListener();
    AppIcon.getInstance().requestAttention(null, true);
    provider.doCheckout(project, listener, repository);

    return CompletableFuture.completedFuture(null);
  }
}
