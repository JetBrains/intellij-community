/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;

import java.util.WeakHashMap;

public class PresentationFactory {
  private final WeakHashMap<AnAction,Presentation> myAction2Presentation;

  public PresentationFactory() {
    myAction2Presentation = new WeakHashMap<AnAction, Presentation>();
  }

  public final Presentation getPresentation(@NotNull AnAction action){
    Presentation presentation = myAction2Presentation.get(action);
    if (presentation == null || !action.isDefaultIcon()){
      presentation = action.getTemplatePresentation().clone();
      myAction2Presentation.put(action, processPresentation(presentation));
    }
    return presentation;
  }

  protected Presentation processPresentation(Presentation presentation) {
    return presentation;
  }

  public void reset() {
    myAction2Presentation.clear();
  }
}
