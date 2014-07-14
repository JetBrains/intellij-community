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
package com.intellij.conversion;

import org.junit.Assert;

import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public class ProjectConversionTestUtil {
  private ProjectConversionTestUtil() {
  }

  public static void assertNoConversionNeeded(String projectPath) {
    final MyConversionListener listener = new MyConversionListener();
    final ConversionResult result = ConversionService.getInstance().convertSilently(projectPath, listener);
    Assert.assertTrue(result.conversionNotNeeded());
    Assert.assertFalse(listener.isConversionNeeded());
    Assert.assertFalse(listener.isConverted());
  }

  public static void convert(String projectPath) {
    final MyConversionListener listener = new MyConversionListener();
    final ConversionResult result = ConversionService.getInstance().convertSilently(projectPath, listener);
    Assert.assertFalse(result.conversionNotNeeded());
    Assert.assertFalse(result.openingIsCanceled());
    Assert.assertTrue(listener.isConversionNeeded());
    Assert.assertTrue(listener.isConverted());
  }

  public static class MyConversionListener implements ConversionListener {
    private boolean myConversionNeeded;
    private boolean myConverted;

    @Override
    public void conversionNeeded() {
      myConversionNeeded = true;
    }

    @Override
    public void successfullyConverted(File backupDir) {
      myConverted = true;
    }

    @Override
    public void error(String message) {
      Assert.fail(message);
    }

    @Override
    public void cannotWriteToFiles(List<File> readonlyFiles) {
    }

    public boolean isConversionNeeded() {
      return myConversionNeeded;
    }

    public boolean isConverted() {
      return myConverted;
    }
  }
}
