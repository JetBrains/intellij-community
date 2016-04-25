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

import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.*;

import static org.jetbrains.java.decompiler.DecompilerTestFixture.assertFilesEqual;
import static org.junit.Assert.assertTrue;


/**
 * Runs all main() methods in, puts stdout output into file.
 *
 */

public class StdoutCompareTestHelper {

  private static DecompilerTestFixture createDecompiler(String externalTempDir) throws IOException {
    List<String> params = new ArrayList<String>();

    params.add(IFernflowerPreferences.RENAME_ENTITIES);
    params.add("1");

    //params.add(IFernflowerPreferences.DECOMPILE_ASSERTIONS);
    //params.add("0");
    
    DecompilerTestFixture fixture;
    fixture = new DecompilerTestFixture();
    fixture.setUp(externalTempDir, params.toArray(new String[0]));
    return fixture;
  }


  private static void runAllMain(List<String> classNames, String outputDir) throws Exception {
    for (String className: classNames) {
      //it could be that we don't find a class because it was renamed on decompile
      //we care about that during output comparison of test (class containing main must not be renamed)
      Method meth = null;
      try {
        Class<?> cl = Class.forName(className);
        meth = cl.getMethod("main", String[].class);
      }
      catch (Exception e) {
        //write a skip file so we know we are skipping tests...
        String outFileName = outputDir + "/" + className + ".skip.txt";
        PrintStream out = new PrintStream(outFileName);
        out.println(e);
        out.close();
      }
      if (meth != null) {
        PrintStream origOut = System.out;
        PrintStream newOut = null;
        PrintStream exceptionOut = null;
        try {
          String outFileName = outputDir + "/" + className + ".output.txt";
          newOut = new PrintStream(outFileName);
          System.setOut(newOut);
          meth.invoke(null, new Object[] {new String[] {}});
        }
        catch (Exception e) {
          //we don't throw here: if no output file exists we know it failed later during test run
          //throw new RuntimeException("Running " + className + " failed: " + e);
          //File exceptionFile = new File(outputDir + "/" + className + ".exception.txt");
          String exceptionFile = outputDir + "/" + className + ".exception.txt";
          exceptionOut = new PrintStream(exceptionFile);
          exceptionOut.append(e.toString() + "\n");
          e.printStackTrace(exceptionOut);
        }
        finally {
          System.setOut(origOut);
          if (newOut != null) {
            newOut.close();
          }
          if (exceptionOut != null) {
            exceptionOut.close();
          }
        }
      }
    }
  }


  public static List<String> getAllClassNames() throws IOException {
    File testDataDir = DecompilerTestFixture.findTestDataDir();
    File stdoutTestsJavaRoot = new File(testDataDir.getPath() + "/src-stdout/");

    List<File> javaFiles = collectFiles(stdoutTestsJavaRoot, ".java");

    //System.out.println("DEBUG: found this java files: " + javaFiles);

    List<String> classNames = new ArrayList<String>();
    for(File f: javaFiles) {
      String n = f.getPath().replace(stdoutTestsJavaRoot.getPath(), "");
      n = n.substring(1);
      n = n.replace(".java", "").replace('/', '.');

      classNames.add(n);
    }
    //System.out.println("DEBUG: found this java names: " + classNames);
    return classNames;
  }


  private static void run(String outputDir) throws Exception {
    List<String> classNames = getAllClassNames();
    runAllMain(classNames, outputDir);
  }


  public static List<File> collectFiles(File dir, String endsWith) {
    //System.out.println("DEBUG: looking for " + endsWith + " files in: " + dir.getPath());
    if (dir == null || !dir.isDirectory()) {
      throw new RuntimeException("ERROR: expecting directory");
    }
    List<File> files = new ArrayList<File>();

    for (File f: dir.listFiles()) {
      if (f.isFile() && f.getName().endsWith(endsWith)) {
        files.add(f);
      }
      else if (f.isDirectory()) {
        files.addAll(collectFiles(f, endsWith));
      }
    }
    return files;
  }

  /**
   * creates the expected output, which is used by the tests to compare to
   * @param args
   */
  public static void main(String[] args) {
    String me = StdoutCompareTestHelper.class.getName();
    try {
      if (args.length < 1) {
        System.err.println("'" + me + "' expects parameters");
        return;
      }
      if (args[0].equals("run")) {
        if (args.length != 2) {
          System.err.println("Correct usage would be '" + me + " run <outputDir>'");
          return;
        }
        else {
          String outputDir = args[1];
          run(outputDir);
        }
      }
      else if (args[0].equals("decompile")) {
        if (args.length != 3) {
          System.err.println("Expecing <decompile-command> <classes_dir> <output_dir>'");
          return;
        }
        DecompilerTestFixture fixture = createDecompiler(args[2]);
        ConsoleDecompiler decompiler = fixture.getDecompiler();
        decompiler.addSpace(new File(args[1]), true);
        decompiler.decompileContext();
      }
      else {
        System.err.println(me + " got unexpected command.");
        return;
      }
    }
    catch (Throwable t) {
      System.err.println(me + " failed: " + t);
    }
  }
}
