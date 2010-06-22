/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 22, 2006
 */
public class StringUtilTest extends TestCase {
  public void testToUpperCase() {
    assertEquals('/', StringUtil.toUpperCase('/'));
    assertEquals(':', StringUtil.toUpperCase(':'));
    assertEquals('A', StringUtil.toUpperCase('a'));
    assertEquals('A', StringUtil.toUpperCase('A'));
    assertEquals('K', StringUtil.toUpperCase('k'));
    assertEquals('K', StringUtil.toUpperCase('K'));
    
    assertEquals('\u2567', StringUtil.toUpperCase(Character.toLowerCase('\u2567')));
  }
  
  public void testToLowerCase() {
    assertEquals('/', StringUtil.toLowerCase('/'));
    assertEquals(':', StringUtil.toLowerCase(':'));
    assertEquals('a', StringUtil.toLowerCase('a'));
    assertEquals('a', StringUtil.toLowerCase('A'));
    assertEquals('k', StringUtil.toLowerCase('k'));
    assertEquals('k', StringUtil.toLowerCase('K'));

    assertEquals('\u2567', StringUtil.toUpperCase(Character.toLowerCase('\u2567')));
  }

  public void testSplitWithQuotes() {
    final List<String> strings = StringUtil.splitHonorQuotes("aaa bbb   ccc \"ddd\" \"e\\\"e\\\"e\"  ", ' ');
    assertEquals(5, strings.size());
    assertEquals("aaa", strings.get(0));
    assertEquals("bbb", strings.get(1));
    assertEquals("ccc", strings.get(2));
    assertEquals("\"ddd\"", strings.get(3));
    assertEquals("\"e\\\"e\\\"e\"", strings.get(4));
  }

  public void testUnpluralize() {
    assertEquals("s", StringUtil.unpluralize("s"));
    assertEquals("z", StringUtil.unpluralize("zs"));
  }

  public void testStartsWithConcatenation() {
    assertTrue(StringUtil.startsWithConcatenationOf("something.withdot", "something", "."));
    assertTrue(StringUtil.startsWithConcatenationOf("something.withdot", "", "something."));
    assertTrue(StringUtil.startsWithConcatenationOf("something.", "something", "."));
    assertTrue(StringUtil.startsWithConcatenationOf("something", "something", ""));
    assertFalse(StringUtil.startsWithConcatenationOf("something", "something", "."));
    assertFalse(StringUtil.startsWithConcatenationOf("some", "something", ""));
  }

  public void testFormattingDate() throws Exception {
    assertEquals("Moments ago", StringUtil.formatDate(new Date().getTime(), SimpleDateFormat.getDateTimeInstance()));
    assertEquals("Few minutes ago", StringUtil.formatDate(new Date().getTime() - (60 * 1000 * 2), SimpleDateFormat.getDateTimeInstance()));
    assertEquals("Last 30 minutes", StringUtil.formatDate(new Date().getTime() - (60 * 1000 * 30), SimpleDateFormat.getDateTimeInstance()));
    assertEquals("Last hour", StringUtil.formatDate(new Date().getTime() - (60 * 1000 * 80), SimpleDateFormat.getDateTimeInstance()));
    assertEquals("2 hours ago", StringUtil.formatDate(new Date().getTime() - (60 * 1000 * 100), SimpleDateFormat.getDateTimeInstance()));
    assertEquals("3 hours ago", StringUtil.formatDate(new Date().getTime() - (60 * 1000 * 60 * 3), SimpleDateFormat.getDateTimeInstance()));
    long t = new Date().getTime() - (60 * 1000 * 60 * 100);
    assertEquals(SimpleDateFormat.getDateTimeInstance().format(t), StringUtil.formatDate(t, SimpleDateFormat.getDateTimeInstance()));
  }
}
