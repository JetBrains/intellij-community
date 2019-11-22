// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.CliResult;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
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
      Messages.showMessageDialog(getUsageMessage(), StringUtil.toTitleCase(getCommandName()), Messages.getInformationIcon());
      return CliResult.error(1, getUsageMessage());
    }
    try {
      return processCommand(args, currentDirectory);
    }
    catch (Exception e) {
      String message = String.format("Error executing %s: %s", getCommandName(), e.getMessage());
      Messages.showMessageDialog(message, StringUtil.toTitleCase(getCommandName()), Messages.getErrorIcon());
      return CliResult.error(1, message);
    }
    finally {
      saveAll();
    }
  }

  protected static void saveAll() {
    FileDocumentManager.getInstance().saveAllDocuments();
    ApplicationManager.getApplication().saveSettings();
  }

  private boolean checkArguments(@NotNull List<String> args) {
    return Arrays.binarySearch(myArgsCount, args.size() - 1) != -1 && getCommandName().equals(args.get(0));
  }

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
  public void main(@NotNull String[] args) {
    int exitCode = 0;
    try {
      Future<CliResult> commandFuture = processCommand(Arrays.asList(args), null);
      CliResult result = commandFuture.get();
      if (result.message != null) {
        System.out.println(result.message);
      }
      exitCode = result.exitCode;
    }
    catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    catch (Throwable t) {
      t.printStackTrace();
      System.exit(2);
    }
    finally {
      saveAll();
    }

    System.exit(exitCode);
  }
}