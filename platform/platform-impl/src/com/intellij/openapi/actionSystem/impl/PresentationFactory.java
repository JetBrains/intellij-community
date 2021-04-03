// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;

public class PresentationFactory {
  private final Map<AnAction, Presentation> actionToPresentation = new WeakHashMap<>();
  private boolean myNeedRebuild;

  private static final Collection<PresentationFactory> ourAllFactories = new WeakList<>();

  public PresentationFactory() {
    ourAllFactories.add(this);
  }

  public final @NotNull Presentation getPresentation(@NotNull AnAction action) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Presentation presentation = actionToPresentation.get(action);
    if (presentation == null || !action.isDefaultIcon()) {
      Presentation templatePresentation = action.getTemplatePresentation();
      if (presentation == null) {
        presentation = templatePresentation.clone();
        actionToPresentation.put(action, presentation);
      }
      if (!action.isDefaultIcon()) {
        presentation.setIcon(templatePresentation.getIcon());
        presentation.setDisabledIcon(templatePresentation.getDisabledIcon());
      }
      processPresentation(presentation);
    }
    return presentation;
  }

  protected void processPresentation(@NotNull Presentation presentation) {
  }

  public void reset() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    actionToPresentation.clear();
    myNeedRebuild = true;
  }

  public boolean isNeedRebuild() {
    return myNeedRebuild;
  }

  public void resetNeedRebuild() {
    myNeedRebuild = false;
  }

  public static void clearPresentationCaches() {
    for (PresentationFactory factory : ourAllFactories) {
      factory.reset();
    }
  }
}
