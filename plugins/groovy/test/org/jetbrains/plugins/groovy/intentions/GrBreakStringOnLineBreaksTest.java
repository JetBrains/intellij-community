// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Max Medvedev
 */
public class GrBreakStringOnLineBreaksTest extends GrIntentionTestCase {
  public GrBreakStringOnLineBreaksTest() {
    super(GroovyIntentionsBundle.message("gr.break.string.on.line.breaks.intention.name"));
  }

  public void testSimple() {
    doTextTest("""
                 print 'ab<caret>c\\ncde'""", """
                 print 'abc\\n' +
                         'cde'""");
  }

  public void testNR() {
    doTextTest("""
                 print 'ab<caret>c\\n\\rcde'""", """
                 print 'abc\\n\\r' +
                         'cde'""");
  }

  public void testGString() {
    doTextTest("""
                 print "a<caret>\\n$x bc\\n\"""", """
                 print "a\\n" +
                         "$x bc\\n\"""");
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = TestUtils.getTestDataPath() + "intentions/breakStringOnLineBreaks/";
}
