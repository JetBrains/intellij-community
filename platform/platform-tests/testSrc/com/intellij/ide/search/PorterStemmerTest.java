/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.search;

import com.intellij.ide.ui.search.PorterStemmerUtil;
import junit.framework.TestCase;

public class PorterStemmerTest extends TestCase {
  public void test() {
    assertEquals("name", PorterStemmerUtil.stem("names"));
    assertEquals("j2ee", PorterStemmerUtil.stem("j2ee"));
    assertEquals("keyword1", PorterStemmerUtil.stem("keyword1"));
    assertEquals("initi", PorterStemmerUtil.stem("initializer"));
    assertEquals("initi", PorterStemmerUtil.stem("initialization"));
    assertEquals("go2file", PorterStemmerUtil.stem("go2file"));
  }

}
