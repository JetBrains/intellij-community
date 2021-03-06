// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Executor {
  private static final Logger LOG = Logger.getInstance(Executor.class);

  private static String ourCurrentDir;

  private static void cdAbs(@NotNull String absolutePath) {
    ourCurrentDir = absolutePath;
    debug("# cd " + shortenPath(absolutePath));
  }

  public static void debug(@NotNull String msg) {
    if (!StringUtil.isEmptyOrSpaces(msg)) {
      LOG.info(msg);
    }
  }

  private static void cdRel(@NotNull String relativePath) {
    cdAbs(ourCurrentDir + "/" + relativePath);
  }

  public static void cd(@NotNull File dir) {
    cdAbs(dir.getAbsolutePath());
  }

  public static void cd(@NotNull Path dir) {
    cdAbs(dir.toAbsolutePath().normalize().toString());
  }

  public static void cd(@NotNull String relativeOrAbsolutePath) {
    if (relativeOrAbsolutePath.startsWith("/") || relativeOrAbsolutePath.charAt(1) == ':') {
      cdAbs(relativeOrAbsolutePath);
    }
    else {
      cdRel(relativeOrAbsolutePath);
    }
  }

  public static void cd(@NotNull VirtualFile dir) {
    cd(dir.getPath());
  }

  public static String pwd() {
    return ourCurrentDir;
  }

  @NotNull
  public static File touch(String filePath) {
    try {
      File file = child(filePath);
      assert !file.exists() : "File " + file + " shouldn't exist yet";
      //noinspection ResultOfMethodCallIgnored
      new File(file.getParent()).mkdirs(); // ensure to create the directories
      boolean fileCreated = file.createNewFile();
      assert fileCreated;
      debug("# touch " + filePath);
      return file;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static File touch(@NotNull String fileName, @NotNull String content) {
    File filePath = touch(fileName);
    echo(fileName, content);
    return filePath;
  }

  public static void echo(@NotNull String fileName, @NotNull String content) {
    try {
      FileUtil.writeToFile(child(fileName), content.getBytes(StandardCharsets.UTF_8), true);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void overwrite(@NotNull String fileName, @NotNull String content) throws IOException {
    overwrite(child(fileName), content);
  }

  public static void overwrite(@NotNull File file, @NotNull String content) throws IOException {
    FileUtil.writeToFile(file, content.getBytes(StandardCharsets.UTF_8), false);
  }

  public static void append(@NotNull File file, @NotNull String content) throws IOException {
    FileUtil.writeToFile(file, content.getBytes(StandardCharsets.UTF_8), true);
  }

  public static void append(@NotNull String fileName, @NotNull String content) throws IOException {
    append(child(fileName), content);
  }

  public static void rm(@NotNull String fileName) {
    rm(child(fileName));
  }

  public static void rm(@NotNull File file) {
    FileUtil.delete(file);
  }

  public static @NotNull File mkdir(@NotNull String dirName) {
    File file = child(dirName);
    boolean dirMade = file.mkdir();
    LOG.assertTrue(dirMade, "Directory " + dirName + " was not created on [" + file.getPath() + "]. " +
                            "list of files in the parent dir: " + Arrays.toString(file.getParentFile().listFiles()));
    debug("# mkdir " + dirName);
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    return file;
  }

  @NotNull
  public static String cat(@NotNull String fileName) {
    try {
      String content = FileUtil.loadFile(child(fileName));
      debug("# cat " + fileName);
      return content;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void cp(@NotNull String fileName, @NotNull File destinationDir) {
    try {
      FileUtil.copy(child(fileName), new File(destinationDir, fileName));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static List<String> splitCommandInParameters(@NotNull String command) {
    List<String> split = new ArrayList<>();

    boolean insideParam = false;
    StringBuilder currentParam = new StringBuilder();
    for (char c : command.toCharArray()) {
      boolean flush = false;
      if (insideParam) {
        if (c == '\'') {
          insideParam = false;
          flush = true;
        }
        else {
          currentParam.append(c);
        }
      }
      else if (c == '\'') {
        insideParam = true;
      }
      else if (c == ' ') {
        flush = true;
      }
      else {
        currentParam.append(c);
      }

      if (flush) {
        if (!StringUtil.isEmptyOrSpaces(currentParam.toString())) {
          split.add(currentParam.toString());
        }
        currentParam = new StringBuilder();
      }
    }

    // last flush
    if (!StringUtil.isEmptyOrSpaces(currentParam.toString())) {
      split.add(currentParam.toString());
    }
    return split;
  }



  @NotNull
  private static String shortenPath(@NotNull String path) {
    String[] split = path.split("/");
    if (split.length > 3) {
      // split[0] is empty, because the path starts from /
      return String.format("/%s/.../%s/%s", split[1], split[split.length - 2], split[split.length - 1]);
    }
    return path;
  }

  @NotNull
  public static File child(@NotNull String fileName) {
    assert ourCurrentDir != null : "Current dir hasn't been initialized yet. Call cd at least once before any other command.";
    return new File(ourCurrentDir, fileName);
  }

  @NotNull
  public static File ourCurrentDir() {
    assert ourCurrentDir != null : "Current dir hasn't been initialized yet. Call cd at least once before any other command.";
    return new File(ourCurrentDir);
  }
}
