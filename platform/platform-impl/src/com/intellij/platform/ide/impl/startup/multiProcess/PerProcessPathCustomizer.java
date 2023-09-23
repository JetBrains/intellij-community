// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.startup.multiProcess;

import com.intellij.openapi.application.PathCustomizer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.platform.ide.bootstrap.StartupUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * An implementation of {@link PathCustomizer} which configures separate config, system and log paths for each process started from the IDE
 * distribution.
 * This is needed to allow running multiple processes of the same IDE.
 */
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "FieldCanBeLocal", "UseOfSystemOutOrSystemErr"})
@ApiStatus.Experimental
@ApiStatus.Internal
public final class PerProcessPathCustomizer implements PathCustomizer {
  private static final String LOCK_FILE_NAME = "process.lock";

  private static Path ourOriginalConfigPath;
  
  // Leave the folder locked until we exit. Store reference to keep CleanerFactory from releasing the file channel.
  @SuppressWarnings("unused") private static FileLock ourConfigLock;

  @Override
  public CustomPaths customizePaths() {
    String isEnabled = System.getenv("JBC_SEPARATE_CONFIG");
    if ("false".equalsIgnoreCase(isEnabled)) {
      return null;
    }
    
    Path oldConfigPath = PathManager.getConfigDir();
    if (StartupUtil.isConfigImportNeeded(oldConfigPath)) {
      StartupUtil.customTargetDirectoryToImportConfig = oldConfigPath;
    }

    Path newConfig;
    Path tempFolder = Paths.get(PathManager.getTempPath());

    int directoryCounter = 0;
    while (true) {
      newConfig = tempFolder.resolve("per_process_config_" + directoryCounter);

      FileLock configLock = tryLockDirectory(newConfig);
      if (configLock != null) {
        ourConfigLock = configLock;
        break;
      }

      if (directoryCounter > 1000) {
        System.err.println("Can't lock temp directories in " + tempFolder);
        return null;
      }

      directoryCounter++;
    }

    Path newSystem = tempFolder.resolve("per_process_system_" + directoryCounter);
    Path baseLogDir = PathManager.getLogDir();
    Path newLog = computeLogDirPath(baseLogDir, directoryCounter);
    if (newLog == null) {
      System.err.println("Can't create log directory in " + baseLogDir);
      return null;
    }
    cleanDirectory(newConfig);
    cleanDirectory(newSystem);
    try {
      CustomConfigFiles.prepareConfigDir(newConfig, oldConfigPath);
    }
    catch (IOException e) {
      System.err.println("Failed to prepare config directory " + newConfig);
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
    ourOriginalConfigPath = oldConfigPath;
    Path startupScriptDir = PathManager.getSystemDir().resolve("startup-script");

    return new CustomPaths(newConfig.toString(), newSystem.toString(), PathManager.getPluginsPath(), newLog.toString(), startupScriptDir);
  }

  private static @Nullable Path computeLogDirPath(Path baseLogDir, int directoryCounter) {
    String namePrefix = DateTimeFormatter.ofPattern("yyyy-MM-dd_'at'_HH-mm-ss").format(LocalDateTime.now());
    String nameSuffix = directoryCounter > 0 ? "_" + directoryCounter : "";
    Path logPath = baseLogDir.resolve(namePrefix + nameSuffix);
    if (!Files.exists(logPath)) {
      return logPath;
    }
    
    /* since this process locks directoryCounter, the log directory with the suggested name may exist only if it was left by the previous 
       process, so let's choose a different name */
    for (int i = 1; i < 1000; i++) {
      Path newLogPath = baseLogDir.resolve(String.format("%s-%03d%s", namePrefix, i, nameSuffix));
      if (!Files.exists(newLogPath)) {
        return newLogPath;
      }
    }
    return null;
  }

  public static @Nullable Path getOriginalConfigPath() {
    return ourOriginalConfigPath;
  }

  @Nullable
  private static FileLock tryLockDirectory(@NotNull Path directory) {
    try {
      Files.createDirectories(directory);

      Path lockFile = directory.resolve(LOCK_FILE_NAME);

      //noinspection resource
      FileChannel fc = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
      return fc.tryLock();
    }
    catch (IOException ignore) {
      return null;
    }
  }

  private static void cleanDirectory(@NotNull Path directory) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
      stream.forEach(path -> {
        if (!path.getFileName().toString().equals(LOCK_FILE_NAME)) {
          try {
            NioFiles.deleteRecursively(path);
          }
          catch (IOException e) {
            System.err.println("Failed to delete " + path + ": " + e);
          }
        }
      });
    }
    catch (IOException e) {
      System.err.println("Failed to clean directory " + directory + ": " + e);
    }
  }
}
