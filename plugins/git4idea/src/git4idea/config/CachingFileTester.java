/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package git4idea.config;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @param <T> test result type
 */
abstract class CachingFileTester<T> {
  @NotNull private final ConcurrentMap<String, TestResult> myFileTestMap = new ConcurrentHashMap<>();

  @NotNull
  synchronized final TestResult getResultForFile(@NotNull String filePath) {
    TestResult result = myFileTestMap.get(filePath);
    long currentLastModificationDate = 0L;

    try {
      currentLastModificationDate = Files.getLastModifiedTime(Paths.get(resolveAgainstEnvPath(filePath))).toMillis();
      if (result == null || result.getFileLastModifiedTimestamp() != currentLastModificationDate) {
        result = new TestResult(testFile(filePath), currentLastModificationDate);
      }
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Exception e) {
      result = new TestResult(e, currentLastModificationDate);
    }

    myFileTestMap.put(filePath, result);
    return result;
  }

  @NotNull
  private static String resolveAgainstEnvPath(@NotNull String filePath) {
    if (!filePath.contains(File.separator)) {
      File exeFile = PathEnvironmentVariableUtil.findInPath(filePath);
      if (exeFile != null) {
        return exeFile.getPath();
      }
    }
    return filePath;
  }

  @Nullable
  public TestResult getCachedResultForFile(@NotNull String filePath) {
    return myFileTestMap.get(filePath);
  }

  @NotNull
  protected abstract T testFile(@NotNull String filePath) throws Exception;

  class TestResult {
    @Nullable private final T myResult;
    @Nullable private final Exception myException;
    private final long myFileLastModifiedTimestamp;

    TestResult(@NotNull T result, long timestamp) {
      myResult = result;
      myFileLastModifiedTimestamp = timestamp;
      myException = null;
    }

    TestResult(@NotNull Exception exception, long timestamp) {
      myFileLastModifiedTimestamp = timestamp;
      myResult = null;
      myException = exception;
    }

    @Nullable
    public T getResult() {
      return myResult;
    }

    @Nullable
    public Exception getException() {
      return myException;
    }

    private long getFileLastModifiedTimestamp() {
      return myFileLastModifiedTimestamp;
    }
  }
}