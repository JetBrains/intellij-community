package com.intellij.openapi.editor.actions;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.FileBasedTestCaseHelper;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Dennis.Ushakov
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
@RunWith(com.intellij.testFramework.Parameterized.class)
@TestDataPath("/testData/../../../platform/platform-tests/testData/editor/matchBrace/")
public class MatchBraceTest extends LightPlatformCodeInsightTestCase implements FileBasedTestCaseHelper {
  @Test
  public void testAction() {
    new WriteCommandAction<Void>(null) {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        configureByFile(getBeforeFileName());
        //EditorTestUtil.setEditorVisibleSize(myEditor, 120, 20); // some actions require visible area to be defined, like EditorPageUp
        executeAction("EditorMatchBrace");
        checkResultByFile(getAfterFileName());
      }
    }.execute();
  }

  @Nullable
  @Override
  public String getFileSuffix(String fileName) {
    int pos = fileName.indexOf("-before.");
    if (pos < 0) {
      return null;
    }
    return pos < 0 ? null : fileName.substring(0, pos) + '(' + fileName.substring(pos + 8) + ')';
  }

  private String getBeforeFileName() {
    int pos = myFileSuffix.indexOf('(');
    return myFileSuffix.substring(0, pos) + "-before." + myFileSuffix.substring(pos + 1, myFileSuffix.length() - 1);
  }

  private String getAfterFileName() {
    int pos = myFileSuffix.indexOf('(');
    return myFileSuffix.substring(0, pos) + "-after." + myFileSuffix.substring(pos + 1, myFileSuffix.length() - 1);
  }
}
