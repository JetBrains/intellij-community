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
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;

public class OutputWrapper extends OutputStream {

  @NotNull private final ExternalSystemTaskNotificationListener myListener;
  @NotNull private final ExternalSystemTaskId myTaskId;
  @Nullable private StringBuilder myBuffer;
  private final boolean myStdOut;

  public OutputWrapper(@NotNull ExternalSystemTaskNotificationListener listener, @NotNull ExternalSystemTaskId taskId, boolean stdOut) {
    myListener = listener;
    myTaskId = taskId;
    myStdOut = stdOut;
  }

  @Override
  public void write(int b) {
    if (myBuffer == null) {
      myBuffer = new StringBuilder();
    }
    char c = (char)b;
    myBuffer.append(Character.toString(c));
    if (c == '\n') {
      doFlush();
    }
  }

  @Override
  public void write(byte[] b, int off, int len) {
    int start = off;
    int maxOffset = off + len;
    for (int i = off; i < maxOffset; i++) {
      if (b[i] == '\n') {
        if (myBuffer == null) {
          myBuffer = new StringBuilder();
        }
        myBuffer.append(new String(b, start, i - start + 1));
        doFlush();
        start = i + 1;
      }
    }

    if (start < maxOffset) {
      if (myBuffer == null) {
        myBuffer = new StringBuilder();
      }
      myBuffer.append(new String(b, start, maxOffset - start));
    }
  }

  private void doFlush() {
    if (myBuffer == null) {
      return;
    }
    myListener.onTaskOutput(myTaskId, myBuffer.toString(), myStdOut);
    myBuffer.setLength(0);
  }
}
