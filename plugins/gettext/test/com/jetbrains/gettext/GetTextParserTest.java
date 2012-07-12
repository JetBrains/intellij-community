package com.jetbrains.gettext;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextParserTest extends LightCodeInsightFixtureTestCase {

  private void doTest(String fileName) throws IOException {
    final String filePath = GetTextUtils.getFullSourcePath(fileName);

    try {
      final String fileText = FileUtil.loadFile(new File(filePath));
      doTest(StringUtil.convertLineSeparators(fileText), fileName);
    }
    catch (IOException e) {
      fail("Can't load file: " + filePath);
    }
  }

  private void doTest(@NonNls final String code, final String fileName) {
    final PsiFile psiFile = createLightFile(fileName, GetTextLanguage.INSTANCE, code);
    final String tree = DebugUtil.psiTreeToString(psiFile, false);
    final String path = GetTextUtils.getFullParserPath(fileName);
    assertSameLinesWithFile(path, tree);
  }

  public void testSimple() throws IOException {
    doTest("command");
  }

  public void testAllFiles() throws Throwable {
    for(final String file : GetTextUtils.getAllTestedFiles()) {
      doTest(file);
    }
  }
}
