/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.ui.playback.util;

import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.LightweightHint;

public class EditorPlaybackCall {

  public static AsyncResult<String> waitForEditorHint(PlaybackContext context) {
    final AsyncResult<String> result = new AsyncResult<String>();
    final Disposable connection = new Disposable() {
      @Override
      public void dispose() {
      }
    };
    result.doWhenProcessed(new Runnable() {
      @Override
      public void run() {
        Disposer.dispose(connection);
      }
    });

    ApplicationManager.getApplication().getMessageBus().connect(connection).subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
      @Override
      public void hintShown(Project project, LightweightHint hint, int flags) {
        result.setDone();
      }
    });

    return result;
  }
}
