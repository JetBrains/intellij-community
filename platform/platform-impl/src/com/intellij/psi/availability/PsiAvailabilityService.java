// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.availability;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public abstract class PsiAvailabilityService {
  /**
   * Commits the document and ensures the corresponding PsiFile's top lazy element is parsed,
   * showing a cancellable modal progress dialog.
   * <p>
   * Should be called on the dispatch thread.
   * <p>
   *
   * @return PSI file for the specified document or null if no PsiFile was found, or the operation was canceled
   */
  public abstract boolean makePsiAvailable(@NotNull Document document, @Nls @NotNull String progressTitle);

  /**
   * Commits the document asynchronously, ensures the corresponding PsiFile's top lazy element is parsed
   * and calls the provided callback immediately after that.
   *
   * If the document was changed before the PSI is ready, the callback won't be called (the caller is expected to
   * reschedule the action via an another `performLaterWhenPsiAvailable()` call, if needed).
   * <p>
   * Should be called on the dispatch thread.
   * The callback is called in the dispatch thread.
   * <p>
   * It's up to the caller to ensure that no several requests are executed simultaniously
   * (possibly cancelling obsolete ones via the provided indicator).
   */
  public abstract void performWhenPsiAvailable(@NotNull Document document,
                                               @NotNull Runnable callback,
                                               @Nullable ProgressIndicator indicator);

  private static final NotNullLazyKey<PsiAvailabilityService, Project> INSTANCE_KEY =
    ServiceManager.createLazyKey(PsiAvailabilityService.class);

  public static PsiAvailabilityService getInstance(@NotNull Project project) {
    return INSTANCE_KEY.getValue(project);
  }
}
