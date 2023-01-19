// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.concatenation;

import com.intellij.openapi.ide.CopyPasteManager;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

import java.awt.datatransfer.DataFlavor;

/**
 * @author Bas Leijdekkers
 */
public class CopyConcatenatedStringToClipboardIntentionTest extends IPPTestCase {

  public void testSimpleLiteral() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.launchAction(myFixture.findSingleIntention(
      IntentionPowerPackBundle.message("copy.string.literal.to.clipboard.intention.name")));
    final Object result = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    assertEquals("simple", result);
  }

  public void testSimpleConcatenation() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.launchAction(myFixture.findSingleIntention(
      IntentionPowerPackBundle.message("copy.concatenated.string.to.clipboard.intention.name")));
    final Object result = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    assertEquals("""
                   <html>
                     <body>
                   ?
                     </body>
                   </html>
                   """, result);
  }

  @Override
  protected String getRelativePath() {
    return "concatenation/copy_concatenated_string_to_clipboard";
  }
}