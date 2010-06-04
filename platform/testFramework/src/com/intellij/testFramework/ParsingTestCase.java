/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.impl.DebugUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

public abstract class ParsingTestCase extends LightPlatformTestCase {
  protected String myFileExt;
  @NonNls private final String myFullDataPath;
  protected PsiFile myFile;

  public ParsingTestCase(@NonNls String dataPath, String fileExt) {
    myFullDataPath = getTestDataPath() + "/psi/" + dataPath;
    myFileExt = fileExt;
  }

  @Override
  protected void tearDown() throws Exception {
    myFile = null;
    super.tearDown();
  }

  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  protected boolean includeRanges() {
    return false;
  }

  protected void doTest(boolean checkResult) throws Exception{
    String name = getTestName(false);
    String text = loadFile(name + "." + myFileExt);
    myFile = createPsiFile(name, text);
    myFile.accept(new PsiRecursiveElementVisitor(){});
    assertEquals(text, myFile.getText());
    if (checkResult){
      checkResult(name + ".txt", myFile);
    }
    else{
      toParseTreeText(myFile, includeRanges());
    }
  }

  protected void doTest(String suffix) throws Exception{
    String name = getTestName(false);
    String text = loadFile(name + "." + myFileExt);
    myFile = createPsiFile(name, text);
    myFile.accept(new PsiRecursiveElementVisitor(){});
    assertEquals(text, myFile.getText());
    checkResult(name + suffix + ".txt", myFile);
  }

  protected PsiFile createPsiFile(String name, String text) {
    return createFile(name + "." + myFileExt, text);
  }

  protected void checkResult(@NonNls @TestDataFile String targetDataName, final PsiFile file) throws Exception {
    doCheckResult(myFullDataPath, file, targetDataName, includeRanges());
  }

  public static void doCheckResult(String myFullDataPath, PsiFile file, String targetDataName, boolean printRanges) throws Exception {
    final PsiElement[] psiRoots = file.getPsiRoots();
    if(psiRoots.length > 1){
      for (int i = 0; i < psiRoots.length; i++) {
        final PsiElement psiRoot = psiRoots[i];
        doCheckResult(myFullDataPath, targetDataName + "." + i, toParseTreeText(psiRoot, printRanges).trim());
      }
    }
    else{
      doCheckResult(myFullDataPath, targetDataName, toParseTreeText(file, printRanges).trim());
    }
  }

  protected void checkResult(@TestDataFile @NonNls String targetDataName, final String text) throws Exception {
    doCheckResult(myFullDataPath, targetDataName, text);
  }

  private static void doCheckResult(String myFullDataPath, String targetDataName, String text) throws Exception {
    try {
      text = text.trim();
      String expectedText = doLoadFile(myFullDataPath, targetDataName);
      assertEquals(expectedText, text);
    }
    catch(FileNotFoundException e){
      String fullName = myFullDataPath + File.separatorChar + targetDataName;
      FileWriter writer = new FileWriter(fullName);
      try {
        writer.write(text);
      }
      finally {
        writer.close();
      }
      fail("No output text found. File " + fullName + " created.");
    }
  }

  protected static String toParseTreeText(final PsiElement file, boolean printRanges) {
    return DebugUtil.psiToString(file, false, printRanges);
  }

  protected String loadFile(@NonNls @TestDataFile String name) throws Exception {
    return doLoadFile(myFullDataPath, name);
  }

  private static String doLoadFile(String myFullDataPath, String name) throws IOException {
    String fullName = myFullDataPath + File.separatorChar + name;
    String text = new String(FileUtil.loadFileText(new File(fullName))).trim();
    text = StringUtil.convertLineSeparators(text);
    return text;
  }
}
