package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Denis Zhdanov
 * @since 23.05.13 15:50
 */
public class OutputWrapper extends OutputStream {

  @NotNull private final ExternalSystemTaskNotificationListener myListener;
  @NotNull private final ExternalSystemTaskId                   myTaskId;

  private final boolean myStdOut;

  public OutputWrapper(@NotNull ExternalSystemTaskNotificationListener listener, @NotNull ExternalSystemTaskId taskId, boolean stdOut) {
    myListener = listener;
    myTaskId = taskId;
    myStdOut = stdOut;
  }

  @Override
  public void write(int b) throws IOException {
    myListener.onTaskOutput(myTaskId, Character.toString((char)b), myStdOut);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    myListener.onTaskOutput(myTaskId, new String(b, off, len), myStdOut);
  }
}
