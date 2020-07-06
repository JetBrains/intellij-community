/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.actionSystem.impl;

import com.intellij.DynamicBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.openapi.actionSystem.Presentation.STRIP_MNEMONIC;

public class PresentationFactory {
  private static final @NotNull NotNullLazyValue<Boolean> hasAnyLanguagePack =
    NotNullLazyValue.createValue(DynamicBundle.LanguageBundleEP.EP_NAME::hasAnyExtensions);

  private final Map<AnAction,Presentation> myAction2Presentation = ContainerUtil.createWeakMap();

  private static final WeakList<PresentationFactory> ourAllFactories = new WeakList<>();

  public PresentationFactory() {
    ourAllFactories.add(this);
  }

  @NotNull
  public final Presentation getPresentation(@NotNull AnAction action){
    ApplicationManager.getApplication().assertIsDispatchThread();
    Presentation presentation = myAction2Presentation.get(action);
    if (presentation == null || !action.isDefaultIcon()){
      Presentation templatePresentation = action.getTemplatePresentation();
      if (presentation == null) {
        presentation = templatePresentation.clone();
        myAction2Presentation.put(action, presentation);
      }
      if (!action.isDefaultIcon()) {
        presentation.setIcon(templatePresentation.getIcon());
        presentation.setDisabledIcon(templatePresentation.getDisabledIcon());
      }
      processPresentation(presentation);
    }
    return presentation;
  }

  protected void processPresentation(Presentation presentation) {
    if (SystemInfo.isMac && hasAnyLanguagePack.getValue()) {
      presentation.putClientProperty(STRIP_MNEMONIC, Boolean.TRUE);
    }
  }

  public void reset() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myAction2Presentation.clear();
  }

  public static void clearPresentationCaches() {
    for (PresentationFactory factory : ourAllFactories) {
      factory.reset();
    }
  }
}
