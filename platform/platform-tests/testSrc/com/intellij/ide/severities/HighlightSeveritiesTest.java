/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 20-Jul-2006
 * Time: 18:26:01
 */
package com.intellij.ide.severities;

import com.intellij.lang.annotation.HighlightSeverity;
import junit.framework.TestCase;
import org.jdom.Element;

public class HighlightSeveritiesTest extends TestCase {
  private int myOldSeverity;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOldSeverity = HighlightSeverity.ERROR.myVal;
  }


  @Override
  protected void tearDown() throws Exception {
    HighlightSeverity.ERROR.myVal = myOldSeverity;
    super.tearDown();
  }

  public void testSeveritiesMigration() throws Exception{
    HighlightSeverity.ERROR.myVal = 200;
    final Element element = new Element("temp");
    new HighlightSeverity(HighlightSeverity.ERROR.myName, 500).writeExternal(element);
    HighlightSeverity.ERROR.readExternal(element);
    assertEquals(500, HighlightSeverity.ERROR.myVal);
  }

  
}