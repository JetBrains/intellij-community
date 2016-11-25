package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

@NonNls
public class GeneralCodeFormatterTest extends LightPlatformTestCase {

  public void testLastLineIndent() throws Exception{
    final String initialText = "a\n";
    final TestFormattingModel model = new TestFormattingModel(initialText);

    model.setRootBlock(new FormattingModelXmlReader(model).readTestBlock("lineIndent"));
    final CommonCodeStyleSettings.IndentOptions indentOptions = new CommonCodeStyleSettings.IndentOptions();
    indentOptions.CONTINUATION_INDENT_SIZE = 8;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.LABEL_INDENT_SIZE = 1;
    final CodeStyleSettings settings = new CodeStyleSettings(false);
    settings.setDefaultRightMargin(120);
    try {
      FormatterEx.getInstanceEx().adjustLineIndent(model, settings, indentOptions, initialText.length() - 1, new TextRange(0, initialText.length()));
    }
    catch (IncorrectOperationException e) {
      fail();
    }

    assertEquals("a\n    ", FormatterImpl.getText(model));

  }

}
