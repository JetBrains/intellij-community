/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class EditorPlaybackCall {

  public static AsyncResult<String> assertEditorLine(final PlaybackContext context, final String expected) {
    final AsyncResult<String> result = new AsyncResult<>();
    WindowSystemPlaybackCall.getUiReady(context).doWhenDone(() -> {
      Editor editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContextFromFocus().getResult());
      if (editor == null) {
        editor = CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(DataManager.getInstance().getDataContextFromFocus().getResult());
      }

      if (editor == null) {
        result.setRejected("Cannot find editor");
        return;
      }


      final int line = editor.getCaretModel().getLogicalPosition().line;
      final int caret = editor.getCaretModel().getOffset();
      final int start = editor.getDocument().getLineStartOffset(line);
      final int end = editor.getDocument().getLineEndOffset(line);

      final StringBuffer actualText = new StringBuffer(editor.getDocument().getText(new TextRange(start, caret)));
      actualText.append("<caret>").append(editor.getDocument().getText(new TextRange(caret, end)));
      if (expected.equals(actualText.toString())) {
        result.setDone();
      } else {
        result.setRejected("Expected:" + expected + " but was:" + actualText);
      }
    });
    
    return result;
  }

  
  public static AsyncResult<String> waitDaemonForFinish(final PlaybackContext context) {
    final AsyncResult<String> result = new AsyncResult<>();
    final Disposable connection = Disposer.newDisposable();
    result.doWhenProcessed(() -> Disposer.dispose(connection));


    WindowSystemPlaybackCall.findProject().doWhenDone(new Consumer<Project>() {
      @Override
      public void consume(Project project) {
        final MessageBusConnection bus = project.getMessageBus().connect(connection);
        bus.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListenerAdapter() {
          @Override
          public void daemonFinished() {
            context.flushAwtAndRunInEdt(result.createSetDoneRunnable());
          }

          @Override
          public void daemonCancelEventOccurred(@NotNull String reason) {
            result.setDone();
          }
        });
      }
    }).doWhenRejected(() -> result.setRejected("Cannot find project"));
    
    return result;
  }
}
