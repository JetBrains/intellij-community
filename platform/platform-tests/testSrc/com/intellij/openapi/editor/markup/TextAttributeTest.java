// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup;

import junit.framework.TestCase;

public class TextAttributeTest extends TestCase {

  public void testMergeDefaultAttributes() {
    TextAttributes attributes = new TextAttributes();
    TextAttributes otherAttributes = new TextAttributes();
    TextAttributes merge = TextAttributes.merge(attributes, otherAttributes);
    assertEquals(attributes, merge);
  }
}
