package org.jetbrains.javafx.testUtils;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.JavaFxFileType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public abstract class FoldingTestBase extends JavaFxLightFixtureTestCase {
  private static final String START_FOLD = "<fold\\stext=\"[^\"]*\">";
  private static final String END_FOLD = "</fold>";

  private class Border implements Comparable<Border> {
    public static final boolean LEFT = true;
    public static final boolean RIGHT = false;
    public final boolean mySide;
    public final int myOffset;
    public final String myText;

    private Border(boolean side, int offset, String text) {
      mySide = side;
      myOffset = offset;
      myText = text;
    }

    public boolean isSide() {
      return mySide;
    }

    public int getOffset() {
      return myOffset;
    }

    public String getText() {
      return myText;
    }

    public int compareTo(Border o) {
      return getOffset() < o.getOffset() ? 1 : -1;
    }
  }

  private String getFoldingDescription(@NotNull final String content) {
    final Application application = ApplicationManager.getApplication();
    application.runWriteAction(new Runnable() {
      public void run() {
        myFixture.configureByText(JavaFxFileType.INSTANCE, content);
      }
    });
    final Editor editor = myFixture.getEditor();
    application.runReadAction(new Runnable() {
      public void run() {
        CodeFoldingManager.getInstance(myFixture.getProject()).buildInitialFoldings(editor);
      }
    });

    final FoldingModel model = editor.getFoldingModel();
    final FoldRegion[] foldingRegions = model.getAllFoldRegions();
    final List<Border> borders = new ArrayList<Border>();

    for (FoldRegion region : foldingRegions) {
      borders.add(new Border(Border.LEFT, region.getStartOffset(), region.getPlaceholderText()));
      borders.add(new Border(Border.RIGHT, region.getEndOffset(), ""));
    }

    Collections.sort(borders);

    StringBuilder result = new StringBuilder(editor.getDocument().getText());
    for (Border border : borders) {
      result.insert(border.getOffset(), border.isSide() == Border.LEFT ? "<fold text=\"" + border.getText() + "\">" : END_FOLD);
    }

    return result.toString();
  }

  public void checkFoldingRegions() {
    final String verificationFileName = getTestDataPath() + "folding/" + getTestName(false) + ".fx";
    String expectedContent = null;
    try {
      expectedContent = new String(FileUtil.loadFileText(new File(verificationFileName)));
    }
    catch (IOException e) {
      fail(e.getMessage());
    }
    Assert.assertNotNull(expectedContent);

    expectedContent = StringUtil.replace(expectedContent, "\r", "");
    final String cleanContent = expectedContent.replaceAll(START_FOLD, "").replaceAll(END_FOLD, "");
    final String actual = getFoldingDescription(cleanContent);

    assertEquals(expectedContent, actual);
  }
}
