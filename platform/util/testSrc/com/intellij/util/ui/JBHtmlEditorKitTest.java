// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.junit.Test;

import javax.swing.*;
import javax.swing.text.StyledDocument;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class JBHtmlEditorKitTest {
  @Test
  public void testLinkedCssIsNotLoaded() throws Exception {
    String htmlWithCssReference =
      "<html><head><link rel='stylesheet' href='" + getClass().getResource("test.css") + "'/></head><body>text</body></html>";
    SwingUtilities.invokeAndWait(() -> {
      JEditorPane pane = new JEditorPane();

      pane.setEditorKit(HTMLEditorKitBuilder.simple());
      pane.setText(htmlWithCssReference);
      assertNotNull(((StyledDocument)pane.getDocument()).getStyle("body")); // style SHOULD be loaded from referenced file in this case

      pane.setEditorKit(new HTMLEditorKitBuilder().withoutContentCss().build());
      pane.setText(htmlWithCssReference);
      assertNull(((StyledDocument)pane.getDocument()).getStyle("body")); // style SHOULD NOT be loaded from referenced file in this case
    });
  }
}
