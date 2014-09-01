/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarterEx;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public abstract class ApplicationStarterBase extends ApplicationStarterEx {
  private final String myCommandName;
  private final int[] myArgsCount;

  protected ApplicationStarterBase(String commandName, int... possibleArgumentsCount) {
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
  public void processExternalCommandLine(String[] args, @Nullable String currentDirectory) {
    if (!checkArguments(args)) {
      Messages.showMessageDialog(getUsageMessage(), StringUtil.toTitleCase(getCommandName()), Messages.getInformationIcon());
      return;
    }
    try {
      processCommand(args, currentDirectory);
    }
    catch (Exception e) {
      Messages.showMessageDialog(String.format("Error showing %s: %s", getCommandName(), e.getMessage()),
                                 StringUtil.toTitleCase(getCommandName()),
                                 Messages.getErrorIcon());
    }
    finally {
      saveAll();
    }
  }

  private static void saveAll() {
    FileDocumentManager.getInstance().saveAllDocuments();
    ApplicationManager.getApplication().saveSettings();
  }

  private boolean checkArguments(String[] args) {
    return Arrays.binarySearch(myArgsCount, args.length - 1) != -1 && getCommandName().equals(args[0]);
  }

  public abstract String getUsageMessage();

  protected abstract void processCommand(String[] args, @Nullable String currentDirectory) throws Exception;

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

  public static VirtualFile findOrCreateFile(String path, @Nullable String currentDirectory) throws IOException {
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path));
    if (file == null) {
      boolean result = new File(path).createNewFile();
      if (result) {
        return findFile(path, currentDirectory);
      }
      else {
        throw new FileNotFoundException("Can't create file " + path);
      }
    }
    return file;
  }

  /**
   * Get direct from file because IDEA cache files(see #IDEA-81067)
   */
  public static String getText(VirtualFile file) throws IOException {
    FileInputStream inputStream = new FileInputStream(file.getPath());
    try {
      return StreamUtil.readText(inputStream);
    }
    finally {
      inputStream.close();
    }
  }

  public static boolean haveDirs(VirtualFile... files) {
    for (VirtualFile file : files) {
      if (file.isDirectory()) {
        return true;
      }
    }
    return false;
  }

  public static boolean areJars(VirtualFile file1, VirtualFile file2) {
    return JarFileSystem.PROTOCOL.equalsIgnoreCase(file1.getExtension()) && JarFileSystem.PROTOCOL.equalsIgnoreCase(file2.getExtension());
  }

  public static boolean areDirs(VirtualFile file1, VirtualFile file2) {
    return file1.isDirectory() && file2.isDirectory();
  }

  public static class OperationFailedException extends IOException {
    public OperationFailedException(@NotNull String message) {
      super(message);
    }
  }

  @NotNull
  public static VirtualFile findFile(final String path, @Nullable String currentDirectory) throws OperationFailedException {
    File ioFile = new File(path);
    if (!ioFile.isAbsolute() && currentDirectory != null) {
      ioFile = new File(currentDirectory, path);
    }
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    if (file == null) {
      throw new OperationFailedException("Can't find file " + path);
    }
    return file;
  }

  @Override
  public boolean canProcessExternalCommandLine() {
    return true;
  }
}
