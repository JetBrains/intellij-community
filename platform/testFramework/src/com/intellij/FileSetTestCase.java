/*
 * Created by IntelliJ IDEA.
 * User: user
 * Date: Sep 22, 2002
 * Time: 2:45:20 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ArrayUtil;
import junit.framework.TestSuite;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class FileSetTestCase extends TestSuite {
  private final File[] myFiles;
  protected Project myProject;
  private Pattern myPattern;

  public FileSetTestCase(String path) {
    File f = new File(path);
    if (f.isDirectory()) {
      myFiles = f.listFiles();
    }
    else if (f.exists()) {
      myFiles = new File[] {f};
    }
    else {
      throw new IllegalArgumentException("invalid path: "     + path);
    }

    final String pattern = System.getProperty("fileset.pattern");
    myPattern = pattern != null ? Pattern.compile(pattern) : null;
    addAllTests();
  }

  protected void setUp() {

  }

  protected void tearDown() {

  }

  private void addAllTests() {
    for (File file : myFiles) {
      if (file.isFile()) {
        addFileTest(file);
      }
    }
  }

  public abstract String transform(String testName, String[] data) throws Exception;

  protected FileSetTestCase(File[] files) {
    myFiles = files;
    addAllTests();
  }

  public String getName() {
    return getClass().getName();
  }

  private void addFileTest(File file) {
    if (!StringUtil.startsWithChar(file.getName(), '_') && !"CVS".equals(file.getName())) {
      if (myPattern != null && !myPattern.matcher(file.getPath()).matches()){
        return;
      }
      final ActualTest t = new ActualTest(file);
      addTest(t);
    }
  }

  protected String getDelimiter() {
    return "---";
  }

  private class ActualTest extends LightPlatformTestCase {
    private final File myTestFile;

    public ActualTest(File testFile) {
      myTestFile = testFile;
    }

    protected void setUp() throws Exception {
      super.setUp();
      FileSetTestCase.this.setUp();
    }

    protected void tearDown() throws Exception {
      FileSetTestCase.this.tearDown();
      super.tearDown();
    }

    public int countTestCases() {
      return 1;
    }

    protected void runTest() throws Throwable {
      String content = new String(FileUtil.loadFileText(myTestFile));
      assertNotNull(content);

      List<String> input = new ArrayList<String>();

      int separatorIndex;

      content = StringUtil.replace(content, "\r", "");

      while ((separatorIndex = content.indexOf(getDelimiter())) >= 0) {
        input.add(content.substring(0, separatorIndex));
        content = content.substring(separatorIndex);
        while (StringUtil.startsWithChar(content, '-') || StringUtil.startsWithChar(content, '\n')) content = content.substring(1);
      }

      String result = content;

      assertTrue("No data found in source file", input.size() > 0);

      while (StringUtil.startsWithChar(result, '-') || StringUtil.startsWithChar(result, '\n') || StringUtil.startsWithChar(result, '\r')) {
        result = result.substring(1);
      }
      final String transformed;
      FileSetTestCase.this.myProject = getProject();
      String testName = myTestFile.getName();
      final int dotIdx = testName.indexOf('.');
      if (dotIdx >= 0) {
        testName = testName.substring(0, dotIdx);
      }

      transformed = StringUtil.replace(transform(testName, ArrayUtil.toStringArray(input)), "\r", "");
      result = StringUtil.replace(result, "\r", "");

      assertEquals(result.trim(),transformed.trim());
    }


    public String toString() {
      return myTestFile.getAbsolutePath() + " ";
    }

    protected void resetAllFields() {
      // Do nothing otherwise myTestFile will be nulled out before getName() is called.
    }

    public String getName() {
      return myTestFile.getAbsolutePath();
    }
  }
}
