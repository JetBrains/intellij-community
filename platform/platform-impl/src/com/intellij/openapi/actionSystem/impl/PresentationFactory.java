// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

public class PresentationFactory {
  private static final Key<Boolean> NEED_UPDATE_PRESENTATION = Key.create("NEED_UPDATE_PRESENTATION");
  private final Map<AnAction, Presentation> myPresentations = CollectionFactory.createConcurrentWeakMap();
  private boolean myNeedRebuild;

  private static final Collection<PresentationFactory> ourAllFactories = new WeakList<>();

  public PresentationFactory() {
    ourAllFactories.add(this);
  }

  public final @NotNull Presentation getPresentation(@NotNull AnAction action) {
    Presentation presentation = myPresentations.get(action);
    boolean needUpdate = presentation != null && presentation.getClientProperty(NEED_UPDATE_PRESENTATION)  == Boolean.TRUE;
    if (presentation == null || !action.isDefaultIcon() || needUpdate) {
      Presentation templatePresentation = action.getTemplatePresentation();
      if (presentation == null) {
        presentation = templatePresentation.clone();
        presentation = ObjectUtils.notNull(myPresentations.putIfAbsent(action, presentation), presentation);
      }
      if (!action.isDefaultIcon() || needUpdate) {
        presentation.setIcon(templatePresentation.getIcon());
        presentation.setDisabledIcon(templatePresentation.getDisabledIcon());
        if (needUpdate) {
          presentation.putClientProperty(NEED_UPDATE_PRESENTATION, null);
        }
      }
      processPresentation(presentation);
    }
    return presentation;
  }

  protected void processPresentation(@NotNull Presentation presentation) {
  }

  public void reset() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myPresentations.clear();
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

  public static void updatePresentation(@NotNull AnAction action)  {
    for (PresentationFactory factory : ourAllFactories) {
      ObjectUtils.consumeIfNotNull(factory.myPresentations.get(action), p -> p.putClientProperty(NEED_UPDATE_PRESENTATION, Boolean.TRUE));
    }
  }
}
