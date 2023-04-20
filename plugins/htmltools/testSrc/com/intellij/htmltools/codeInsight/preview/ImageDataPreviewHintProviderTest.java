package com.intellij.htmltools.codeInsight.preview;

import com.intellij.codeInsight.preview.PreviewHintComponent;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import javax.swing.*;

/**
 * Created by fedorkorotkov.
 */
public class ImageDataPreviewHintProviderTest extends LightJavaCodeInsightFixtureTestCase {
  public void testDataUriImage() {
    myFixture.configureByText("test.html", """
      <html>
      <head>
      </head>
      <body>
      <img src="dat<caret>a:image/gif;base64,R0lGODlhAQABAID/AMDAwAAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==">
      </body>
      </html>""");
    doTest();
  }

  private void doTest() {
    final HtmlPreviewHintProvider provider = new HtmlPreviewHintProvider();
    final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    assertNotNull(elementAt);
    final JComponent previewComponent = provider.getPreviewComponent(elementAt);
    assertInstanceOf(previewComponent, PreviewHintComponent.class);
  }
}
