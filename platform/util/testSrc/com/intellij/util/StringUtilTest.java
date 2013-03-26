package com.intellij.util;

import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

/**
 * @author VISTALL
 * @since 18:15/26.03.13
 */
public class StringUtilTest extends TestCase {
  public void testIsEmptyOrSpaces() throws Exception {
    assertTrue(StringUtil.isEmptyOrSpaces(null));
    assertTrue(StringUtil.isEmptyOrSpaces(""));
    assertTrue(StringUtil.isEmptyOrSpaces("                   "));

    assertFalse(StringUtil.isEmptyOrSpaces("1"));
    assertFalse(StringUtil.isEmptyOrSpaces("         12345          "));
    assertFalse(StringUtil.isEmptyOrSpaces("test"));
  }
}
