// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

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

  @Override
  public void processExternalCommandLine(@NotNull String[] args, @Nullable String currentDirectory) {
    if (!checkArguments(args)) {
      Messages.showMessageDialog(getUsageMessage(), StringUtil.toTitleCase(getCommandName()), Messages.getInformationIcon());
      return;
    }
    try {
      processCommand(args, currentDirectory);
    }
    catch (Exception e) {
      String message = String.format("Error executing %s: %s", getCommandName(), e.getMessage());
      Messages.showMessageDialog(message, StringUtil.toTitleCase(getCommandName()), Messages.getErrorIcon());
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

  protected abstract void processCommand(@NotNull String[] args, @Nullable String currentDirectory) throws Exception;

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
}