package com.intellij.lang.ant.config.execution;

import com.intellij.execution.filters.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.OutputStream;

public final class PlainTextView implements AntOutputView {

  private final ConsoleView myConsole;
  private final Project myProject;
  private String myCommandLine;
  private final LightProcessHandler myProcessHandler = new LightProcessHandler();

  public PlainTextView(Project project) {
    myProject = project;
    TextConsoleBuilder builder = TextConsoleBuidlerFactory.getInstance().createBuilder(project);
    builder.addFilter(new AntMessageFilter());
    builder.addFilter(new RegexpFilter(project, "$FILE_PATH$\\s+\\($LINE$\\:$COLUMN$\\)"));
    builder.addFilter(new JUnitFilter());
    myConsole = builder.getConsole();
    myConsole.attachToProcess(myProcessHandler);
  }

  public void dispose() {
    myConsole.dispose();
  }

  public JComponent getComponent() {
    return myConsole.getComponent();
  }

  @Nullable
  public Object addMessage(AntMessage message) {
    print(message.getText() + "\n", ProcessOutputTypes.STDOUT);
    return null;
  }

  private void print(String text, Key type) {
    myProcessHandler.notifyTextAvailable(text, type);
  }

  public void addMessages(AntMessage[] messages) {
    for (AntMessage message : messages) {
      addMessage(message);
    }
  }

  public void addJavacMessage(AntMessage message, String url) {
    final VirtualFile file = message.getFile();
    if (message.getLine() > 0) {
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {

        if (file != null) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              String presentableUrl = file.getPresentableUrl();
              builder.append(presentableUrl);
              builder.append(' ');
            }
          });
        }
        else if (url != null) {
          builder.append(url);
          builder.append(' ');
        }
        builder.append('(');
        builder.append(message.getLine());
        builder.append(':');
        builder.append(message.getColumn());
        builder.append(")");
        print(builder.toString(), ProcessOutputTypes.STDOUT);
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    print(message.getText(), ProcessOutputTypes.STDOUT);
  }

  public void addException(AntMessage exception, boolean showFullTrace) {
    String text = exception.getText();
    if (!showFullTrace) {
      int index = text.indexOf("\r\n");
      if (index != -1) {
        text = text.substring(0, index) + "\n";
      }
    }
    print(text, ProcessOutputTypes.STDOUT);
  }

  public void clearAllMessages() {
    myConsole.clear();
  }

  public void startBuild(AntMessage message) {
    print(myCommandLine + "\n", ProcessOutputTypes.SYSTEM);
    addMessage(message);
  }

  public void buildFailed(AntMessage message) {
    print(myCommandLine + "\n", ProcessOutputTypes.SYSTEM);
    addMessage(message);
  }

  public void startTarget(AntMessage message) {
    addMessage(message);
  }

  public void startTask(AntMessage message) {
    addMessage(message);
  }

  public void finishBuild(String messageText) {
    print("\n" + messageText + "\n", ProcessOutputTypes.SYSTEM);
  }

  public void finishTarget() {
  }

  public void finishTask() {
  }

  @Nullable
  public Object getData(String dataId) {
    return null;
  }

  public void setBuildCommandLine(String commandLine) {
    myCommandLine = commandLine;
  }

  private final class JUnitFilter implements Filter {
    @Nullable
    public Result applyFilter(String line, int entireLength) {
      HyperlinkUtil.PlaceInfo placeInfo = HyperlinkUtil.parseJUnitMessage(myProject, line);
      if (placeInfo == null) {
        return null;
      }

      int textStartOffset = entireLength - line.length();
      int highlightStartOffset = textStartOffset + placeInfo.getLinkStartIndex();
      int highlightEndOffset = textStartOffset + placeInfo.getLinkEndIndex() + 1;

      OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, placeInfo.getFile(), placeInfo.getLine(), placeInfo.getColumn());
      OpenFileHyperlinkInfo info = new OpenFileHyperlinkInfo(descriptor);
      return new Result(highlightStartOffset, highlightEndOffset, info);
    }
  }

  private final class AntMessageFilter implements Filter {
    public Result applyFilter(String line, int entireLength) {
      int afterLineNumberIndex = line.indexOf(": "); // end of file_name_and_line_number sequence
      if (afterLineNumberIndex == -1) {
        return null;
      }

      String fileAndLineNumber = line.substring(0, afterLineNumberIndex);
      int index = fileAndLineNumber.lastIndexOf(':');

      if (index == -1) {
        return null;
      }

      final String fileName = fileAndLineNumber.substring(0, index);
      String lineNumberStr = fileAndLineNumber.substring(index + 1, fileAndLineNumber.length()).trim();
      int lineNumber;
      try {
        lineNumber = Integer.parseInt(lineNumberStr);
      }
      catch (NumberFormatException e) {
        return null;
      }

      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(fileName.replace(File.separatorChar, '/'));
      if (file == null) {
        return null;
      }

      int textStartOffset = entireLength - line.length();
      int highlightEndOffset = textStartOffset + afterLineNumberIndex;

      OpenFileHyperlinkInfo info = new OpenFileHyperlinkInfo(myProject, file, lineNumber - 1);
      return new Result(textStartOffset, highlightEndOffset, info);
    }
  }

  private static class LightProcessHandler extends ProcessHandler {
    protected void destroyProcessImpl() {
      throw new UnsupportedOperationException();
    }

    protected void detachProcessImpl() {
      throw new UnsupportedOperationException();
    }

    public boolean detachIsDefault() {
      return false;
    }

    @Nullable
    public OutputStream getProcessInput() {
      return null;
    }
  }
}
