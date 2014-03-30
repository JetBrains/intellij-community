package com.intellij.conversion;

import junit.framework.Assert;

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
