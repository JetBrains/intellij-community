/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.testcases;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightIdeaTestCase;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.FileScanner;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides main functionality for unit testing using sets of test files.
 * Adds all testfiles to current testsuite.
 *
 * @author Ilya.Sergey
 */
public abstract class FileSetTestCase extends TestSuite {
  @NonNls
  protected static final String TEST_FILE_PATTERN = "(.*)\\.test";
  private File[] myFiles;

  public FileSetTestCase(String path) {
    List<File> myFileList;
    try {
      myFileList = FileScanner.scan(path, getSearchPattern(), false);
    } catch (FileNotFoundException e) {
      myFileList = new ArrayList<File>();
    }
    myFiles = myFileList.toArray(new File[myFileList.size()]);
    addAllTests();
  }

  protected void setUp() {
  }

  protected void tearDown() {
  }

  private void addAllTests() {
    for (File f : myFiles) {
      if (f.isFile()) {
        addFileTest(f);
      }
    }
  }

  protected FileSetTestCase(File[] files) {
    myFiles = files;
    addAllTests();
  }

  public String getName() {
    return getClass().getName();
  }

  public String getSearchPattern() {
    return TEST_FILE_PATTERN;
  }

  protected void addFileTest(File file) {
    if (!StringUtil.startsWithChar(file.getName(), '_') &&
            !"CVS".equals(file.getName())) {
      final ActualTest t = new ActualTest(file);
      addTest(t);
    }
  }

  protected abstract void runTest(final File file) throws Throwable;

  private class ActualTest extends LightIdeaTestCase {
    private File myTestFile;

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

    protected void runTest() throws Throwable {
      FileSetTestCase.this.runTest(myTestFile);
    }

    public int countTestCases() {
      return 1;
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
