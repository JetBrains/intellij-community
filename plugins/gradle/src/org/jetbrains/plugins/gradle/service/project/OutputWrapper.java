package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Denis Zhdanov
 * @since 23.05.13 15:50
 */
public class OutputWrapper extends OutputStream {

  @NotNull private final ExternalSystemTaskNotificationListener myListener;
  @NotNull private final ExternalSystemTaskId                   myTaskId;

  @Nullable private StringBuilder myBuffer;

  private final boolean myStdOut;

  public OutputWrapper(@NotNull ExternalSystemTaskNotificationListener listener, @NotNull ExternalSystemTaskId taskId, boolean stdOut) {
    myListener = listener;
    myTaskId = taskId;
    myStdOut = stdOut;
  }

  @Override
  public void write(int b) throws IOException {
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
  public void write(byte[] b, int off, int len) throws IOException {
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
