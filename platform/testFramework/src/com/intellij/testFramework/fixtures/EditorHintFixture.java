/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.Nullable;

public class EditorHintFixture implements EditorHintListener {
  private LightweightHint myCurrentHint;
  
  public EditorHintFixture(Disposable parentDisposable) {
    ApplicationManager.getApplication().getMessageBus().connect(parentDisposable).subscribe(EditorHintListener.TOPIC, this);
  }

  @Override
  public void hintShown(Project project, LightweightHint hint, int flags) {
    hint.putUserData(LightweightHint.SHOWN_AT_DEBUG, Boolean.TRUE);
    myCurrentHint = hint;
    hint.addHintListener(event -> {
      LightweightHint source = (LightweightHint)event.getSource();
      source.putUserData(LightweightHint.SHOWN_AT_DEBUG, null);
      if (source == myCurrentHint) myCurrentHint = null;
    });
  }
  
  @Nullable
  public String getCurrentHintText() {
    return myCurrentHint == null ? null : myCurrentHint.getComponent().toString();
  }
}
