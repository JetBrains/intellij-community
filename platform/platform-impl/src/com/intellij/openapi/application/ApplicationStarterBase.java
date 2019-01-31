// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.CliResult;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.Future;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public abstract class ApplicationStarterBase extends ApplicationStarterEx {
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

  @NotNull
  @Override
  public Future<? extends CliResult> processExternalCommandLineEx(@NotNull String[] args, @Nullable String currentDirectory) {
    if (!checkArguments(args)) {
      Messages.showMessageDialog(getUsageMessage(), StringUtil.toTitleCase(getCommandName()), Messages.getInformationIcon());
      return CliResult.error(1, getUsageMessage());
    }
    try {
      return processCommand(args, currentDirectory);
    }
    catch (Exception e) {
      Messages.showMessageDialog(String.format("Error showing %s: %s", getCommandName(), e.getMessage()),
                                 StringUtil.toTitleCase(getCommandName()),
                                 Messages.getErrorIcon());
      return CliResult.error(1, String.format("Error showing %s: %s", getCommandName(), e.getMessage()));
    }
    finally {
      saveAll();
    }
  }

  protected static void saveAll() {
    FileDocumentManager.getInstance().saveAllDocuments();
    ApplicationManager.getApplication().saveSettings();
  }

  private boolean checkArguments(String[] args) {
    return Arrays.binarySearch(myArgsCount, args.length - 1) != -1 && getCommandName().equals(args[0]);
  }

  public abstract String getUsageMessage();

  @NotNull
  protected abstract Future<? extends CliResult> processCommand(@NotNull String[] args, @Nullable String currentDirectory) throws Exception;

  @Override
  public void premain(String[] args) {
    if (!checkArguments(args)) {
      System.err.println(getUsageMessage());
      System.exit(1);
    }
  }

  @Override
  public void main(String[] args) {
    try {
      processCommand(args, null);
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

    System.exit(0);
  }

  @Override
  public boolean canProcessExternalCommandLine() {
    return true;
  }
}
