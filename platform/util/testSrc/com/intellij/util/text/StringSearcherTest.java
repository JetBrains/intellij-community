/*
 * @author Eugene Zhuravlev
 */
package com.intellij.util.text;

import junit.framework.TestCase;

public class StringSearcherTest extends TestCase {
  public void testSearchPatternAtTheEnd() {
    final String pattern = "bc";
    final String text = "aabc";

    StringSearcher searcher = new StringSearcher(pattern, true, true);
    final int index = searcher.scan(text);

    assertEquals(text.indexOf("bc"), index);
  }

}
