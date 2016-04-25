/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static org.jetbrains.java.decompiler.DecompilerTestFixture.assertFilesEqual;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class StdoutCompareTestRunner {
  private String stdoutExpectedPath;
  private String stdoutPath;
  private boolean skip;

  private static String expectedBaseDir = System.getProperty("stdout-tests.expected.dir");
  private static String decompileBaseDir = System.getProperty("stdout-tests.decompile.dir");

  public StdoutCompareTestRunner(String testName, String stdoutExpectedPath, String stdoutPath, boolean skip) {
    this.stdoutExpectedPath = stdoutExpectedPath;
    this.stdoutPath = stdoutPath;
    this.skip = skip;
  }

  /**
   * @return list with constructor arguments, first one is used in test name
   */
  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    Collection<Object[]> ctorParams = new ArrayList<Object[]>();

    File expectedDir = new File(expectedBaseDir + "/results");
    FilenameFilter filter = new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(".output.txt") || name.endsWith(".skip.txt");
      }
    };
    File[] files = expectedDir.listFiles(filter);
    if (files != null) {
      for(File f: files) {
        String normalName = decompileBaseDir + "/results/" + f.getName();
        String caseName;
        boolean skip = false;
        if (f.getName().endsWith(".output.txt")) {
          caseName = f.getName().replace(".output.txt", "");
        }
        else {
          caseName = f.getName().replace(".skip.txt", "");
          skip = true;
        }
        ctorParams.add(new Object[] { caseName, f.getPath(), normalName, skip});
      }
    }
    return ctorParams;
  }



  @Before
  public void setUp() throws IOException {
  }

  @After
  public void tearDown() {
  }


  @Test
  public void compareStdoutTest() throws Exception{
    //skip test if the expected output was 'skip'
    Assume.assumeFalse("Skipped because no expected output. That normally means test is disabled per stdout-tests.exclude.pattern from build.xml.", skip);
    
    File stdoutExpected = new File(stdoutExpectedPath);
    File stdout = new File(stdoutPath);

    assertTrue("Error in test, this should not happen (expected output not found)", stdoutExpected.isFile());
    assertTrue("Did not find output (was compilation of case disabled (see build.xml) because it would fail?), at: " + stdoutExpected.getPath(), stdout.isFile());

    assertFilesEqual(stdoutExpected, stdout);

    File exceptionFile = new File(stdout.getPath().replace(".output.txt", ".exception.txt"));
    File exceptionExpectedFile = new File(stdoutExpected.getPath().replace(".output.txt", ".exception.txt"));

    //main of tests should not throw!!! but we check if it did in run of original classes too
    assertTrue("Original classes did throw from main on running, fix the test.", !exceptionExpectedFile.isFile());
    assertTrue("The decompiled code threw an unexpected exception: " + exceptionFile.getPath(), !exceptionFile.isFile());

    validatePatterns(stdoutExpected, stdout);
  }

  private void validatePatterns(File stdoutExpected, File stdout) throws Exception {

    //check stdout if there are patterns for it provided
    //construct path of pattern file:
    String baseFileName = stdoutExpected.getAbsolutePath().replace(new File(expectedBaseDir).getAbsolutePath() + "/results/", "");
    baseFileName = baseFileName.replace(".output.txt", "").replace(".", "/");

    File testData = DecompilerTestFixture.findTestDataDir();
    File patternFile = new File(testData.getPath() + "/src-stdout/" + baseFileName  + ".pattern.txt");
    //System.out.println("DEBUG: looking for pattern file: " + patternFile.getPath());
    if (patternFile.isFile()) {
      //System.out.println("DEBUG: pattern file found: " + patternFile.getPath());

      //get decompiled java file:
      String javaBaseDir = decompileBaseDir + "/decompiled/";

      String javaFilePath = javaBaseDir + baseFileName.replace(".", "/") + ".java";
      String javaContent = new String(Files.readAllBytes(Paths.get(javaFilePath)));

      List<String[]> patterns = parsePatternFile(patternFile);
      for (String[] p: patterns) {
        //System.out.println("DEBUG: using pattern (" + p[0] + "): " + p[1]);


        Pattern pat = Pattern.compile(".*?" + p[1] + ".*?", Pattern.DOTALL | Pattern.MULTILINE);
        if (p[0].equals("matches")) {
          assertTrue("Matches regex (" + p[1] + ") failed on: " + javaFilePath, pat.matcher(javaContent).matches());
        }
        else {
          assertTrue("Not matches regex (" + p[1] + ") failed on " + javaFilePath, !pat.matcher(javaContent).matches());
        }
      }
    }
  }

  /**
   * @return entrys: match_type {'matches', '!matches'}, regex
   */
  private List<String[]> parsePatternFile(File f) throws Exception {
    // 
    List<String[]> res = new ArrayList<String[]>();
    BufferedReader br = new BufferedReader(new FileReader(f));
    String cur = null;
    while ((cur = br.readLine()) != null) {
      String errMsg = "Pattern file (" + f.getPath() + ") syntax error in line: " + cur;
      cur = cur.trim();
      if (cur.length() == 0 || cur.charAt(0) == '#') {
        continue;
      }
      assertTrue(errMsg, cur.indexOf(':') > 0);
      String t = cur.substring(0, cur.indexOf(':'));
      String v = cur.substring(cur.indexOf(':') + 1, cur.length()).trim();

      assertTrue(errMsg, t.equals("matches") || t.equals("!matches"));
      res.add(new String[] { t, v});
    }
    br.close();
    return res;
  }
}


