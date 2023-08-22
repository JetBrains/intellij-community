// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.tests.IgnoreException;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestListener;
import org.junit.runner.Describable;
import org.junit.runner.Description;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Some utilities for out-of-process retries
 */
public final class OutOfProcessRetries {
  public static OutOfProcessRetryListener getListenerForOutOfProcessRetry() {
    String property = System.getProperty("intellij.build.test.retries.failedClasses.file");
    if (property == null) return null;
    return new OutOfProcessRetryListener(property);
  }

  /**
   * Stores list of classes with failed tests to a file.
   * Later that file could be passed to a `intellij.build.test.list.file` system property to run only those test classes.
   */
  public static class OutOfProcessRetryListener implements TestListener {
    private final String myFileWithFailedClassesPath;
    private final Collection<String> myRetryClasses = new LinkedHashSet<>();

    private OutOfProcessRetryListener(String fileWithFailedClassesPath) {
      myFileWithFailedClassesPath = fileWithFailedClassesPath;
    }

    public void addError(String className, Throwable e) {
      if (IgnoreException.isIgnoringThrowable(e)) {
        return;
      }
      myRetryClasses.add(className);
    }

    public void save() throws IOException {
      myRetryClasses.remove("_FirstInSuiteTest");
      myRetryClasses.remove("_LastInSuiteTest");
      Path path = Path.of(myFileWithFailedClassesPath);
      Files.createDirectories(path.getParent());
      Files.write(path, myRetryClasses);
    }

    //region TestListener
    @Override
    public void addError(Test test, Throwable e) {
      if (IgnoreException.isIgnoringThrowable(e)) {
        return;
      }
      String className = getClassName(test);
      myRetryClasses.add(className);
    }

    @Override
    public void addFailure(Test test, AssertionFailedError e) {
      addError(test, e);
    }

    @Override
    public void endTest(Test test) {
    }

    @Override
    public void startTest(Test test) {
    }
    //endregion

    private static String getClassName(Test test) {
      if (test instanceof Describable) {
        Description description = ((Describable)test).getDescription();
        String name = getClassName(description);
        if (name != null) return name;
      }
      return test.getClass().getName();
    }

    private static String getClassName(Description description) {
      try {
        return description.getClassName();
      }
      catch (NoSuchMethodError e) {
        final String displayName = description.getDisplayName();
        Matcher matcher = Pattern.compile("(.*)\\((.*)\\)").matcher(displayName);
        return matcher.matches() ? matcher.group(2) : displayName;
      }
    }
  }
}
