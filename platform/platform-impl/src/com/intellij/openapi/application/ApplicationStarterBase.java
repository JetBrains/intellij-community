// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.CliResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public abstract class ApplicationStarterBase implements ApplicationStarter {
  private final String myCommandName;
  private final int[] myArgsCount;

  protected ApplicationStarterBase(@NotNull String commandName, int... possibleArgumentsCount) {
    myCommandName = commandName;
    myArgsCount = possibleArgumentsCount;
  }

  @Override
  public String getCommandName() {
    return myCommandName;
  }

  @Override
  public boolean isHeadless() {
    return false;
  }

  @Override
  public boolean canProcessExternalCommandLine() {
    return true;
  }

  @NotNull
  @Override
  public Future<CliResult> processExternalCommandLineAsync(@NotNull List<String> args, @Nullable String currentDirectory) {
    if (!checkArguments(args)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        Messages.showMessageDialog(getUsageMessage(), StringUtil.toTitleCase(getCommandName()), Messages.getInformationIcon());
      });
      return CliResult.error(1, getUsageMessage());
    }
    try {
      return processCommand(args, currentDirectory);
    }
    catch (Exception e) {
      String message = String.format("Error executing %s: %s", getCommandName(), e.getMessage());
      ApplicationManager.getApplication().invokeLater(() -> {
        Messages.showMessageDialog(message, StringUtil.toTitleCase(getCommandName()), Messages.getErrorIcon());
      });
      return CliResult.error(1, message);
    }
  }

  protected static void saveAll() {
    FileDocumentManager.getInstance().saveAllDocuments();
    ApplicationManager.getApplication().saveSettings();
  }

  protected static void saveIfNeeded(@Nullable VirtualFile file) {
    if (file == null) return;
    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document != null) FileDocumentManager.getInstance().saveDocument(document);
  }

  private boolean checkArguments(@NotNull List<String> args) {
    return Arrays.binarySearch(myArgsCount, args.size() - 1) != -1 && getCommandName().equals(args.get(0));
  }

  @NlsContexts.DialogMessage
  public abstract String getUsageMessage();

  @NotNull
  protected abstract Future<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) throws Exception;

  @Override
  public void premain(@NotNull List<String> args) {
    if (!checkArguments(args)) {
      System.err.println(getUsageMessage());
      System.exit(1);
    }
  }

  @Override
  public void main(@NotNull List<String> args) {
    try {
      int exitCode;
      try {
        Future<CliResult> commandFuture = processCommand(args, null);
        CliResult result = commandFuture.get();
        if (result.message != null) {
          System.out.println(result.message);
        }
        exitCode = result.exitCode;
      }
      finally {
        ApplicationManager.getApplication().invokeAndWait(ApplicationStarterBase::saveAll);
      }
      System.exit(exitCode);
    }
    catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    catch (Throwable t) {
      t.printStackTrace();
      System.exit(2);
    }
  }
}