/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.formatting.engine

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Test

/**
 * Just a GeneralCodeFormatterTest.java migration to simplified formatting model specification
 */
class GeneralCodeFormatterTest : LightPlatformTestCase() {

  @Test
  fun `test null indent is treated as continuation indent`() {
    doReformatTest(
      """
[]aaa []bbb []ccc
[]ddd []eee []fff
""",
      """
aaa bbb ccc
        ddd eee fff
"""
    )
  }

  @Test
  fun `test continuation indent`() {
    doReformatTest(
      """
[i_none]a
[i_none]([i_cont]b
[i_cont]c)
""",
      """
a
        b
        c
"""
    )
  }

  @Test
  fun `test normal indent`() {
    doReformatTest(
      """
[i_none]a
[i_none]([i_norm]b
[i_norm]c)
""",
      """
a
    b
    c
"""
    )
  }

  @Test
  fun `test many nested blocks and continuation indent`() {
    doReformatTest(
      """
[]([]([]([]a
[]b
[]([]([]c
[]d
[]([]e
[]f))))))
""",
      """
a
        b
        c
                d
                e
                        f
"""
    )
  }

  @Test
  fun `test indents composition`() {
    val settings = CodeStyleSettings()
    settings.indentOptions!!.LABEL_INDENT_SIZE = 1
    doReformatTest("""
[i_none]aaa [i_none]bbb
[i_none]([i_norm]ccc [i_norm]ddd
[i_norm]([i_label]eee
[i_label]fff))
""",
                   """
aaa bbb
    ccc ddd
     eee
     fff
""", settings)
  }

  @Test
  fun `test alignments on different block levels`() {
    doReformatTest(
      """
[]aaa [a1]bbb
[a1]([i_label]ccc [i_label]ddd
[i_label a1]([]eee
[a1]fff))
""",
      """
aaa bbb
    ccc ddd
    eee
    fff
"""
    )
  }

  @Test
  fun `test nested indents`() {
    doReformatTest(
      """
[]xxx
[i_cont]([i_cont]([i_cont]yyy))
""",
      """
xxx
                        yyy
"""
    )
  }

  @Test
  fun `test one more alignment test`() {
    doReformatTest(
      """
[]aaa [a1]bbb [a1]([i_label]ccc [i_label]ddd [i_label a1]([]eee
[a1]fff))
""",
      """
aaa bbb ccc ddd eee
    fff
"""
    )
  }

  @Test
  fun `test trivial spaces`() {
    doReformatTest(
      """
[]foo [s_min3]goo
""",
      """
foo   goo
"""
    )
  }

  @Test
  fun `test space properties`() {
    val settings = CodeStyleSettings()
    settings.indentOptions!!.LABEL_INDENT_SIZE = 1
    doReformatTest(
      """
[]aaa [s_min2_max2]bbb [s_min2_max2]([i_norm]ccc
[i_norm s_min2_max2]ddd [i_norm s_min2_max2]([i_label]eee [i_label s_min2_max2_minlf2]fff))
""",
      """
aaa  bbb  ccc
    ddd  eee

     fff
""", settings)
  }

  @Test
  fun `test remove all spaces`() {
    doReformatTest(
      """
[s_min0_max0_keepLb0]0 [s_min0_max0_keepLb0]1
[s_min0_max0_keepLb0]2
[s_min0_max0_keepLb0]3 [s_min0_max0_keepLb0]4 [s_min0_max0_keepLb0]5
[s_min0_max0_keepLb0]6 [s_min0_max0_keepLb0]7 [s_min0_max0_keepLb0]8 [s_min0_max0_keepLb0]9 """,
      "0123456789 ")
  }


  @Test
  fun `test first spacing object is used`() {
    doReformatTest("[]0 [s_min5_max5]([s_min10_max10]1)", "0     1")
  }

  @Test 
  fun `test no wrap object no text wrap`() {
    doReformatTest("[]aaa []bbb []ccc []ddd []eee []f|ff", "aaa bbb ccc ddd eee fff")
  }

  @Test
  fun `test simple wrap`() {
    doReformatTest(
      """
[]aaa [w_normal i_cont]b|bb
""", 
      """
aaa
        bbb
""")
  }

  @Test
  fun `test wrap`() {
    val settings = CodeStyleSettings()
    settings.indentOptions!!.LABEL_INDENT_SIZE = 1
    
    doReformatTest(
"[i_none w_always]aaa " +
"[i_none s_min2_max2 w_always]bbb " +
"[i_none s_min2_max2]" +
     "([i_label w_always]ccc " +
      "[i_label s_min2_max2 w_always]ddd " +
      "[i_label s_min2_max2]([w_always]eee " +
                            "[s_min2_max2 w_always]fff))",

"""
aaa
bbb
 ccc
 ddd
 eee
         fff""", settings)
  }

  @Test
  fun `test wrap one more time`() {
    val settings = CodeStyleSettings()
    settings.indentOptions!!.LABEL_INDENT_SIZE = 1
    
    doReformatTest(
"[w_normal]aaa " +
"[s_min2_max2 w_normal]bbb " +
"[s_min2_max2 w_normal]" +
  "([i_label]ccc " +
  "[i_label s_min2_max2 w_normal]ddd |" +
  "[i_label s_min2_max2 w_normal]" +
    "([]eee [s_min2_max2 w_normal]fff))",
"""aaa  bbb  ccc
 ddd  eee  fff""", settings)
  }

  @Test
  fun `test chop down`() {
    val settings = CodeStyleSettings()
    settings.indentOptions!!.LABEL_INDENT_SIZE = 1
    
    doReformatTest(
      "[w_chop1]aaa [s_min2_max2 w_chop1]bbb " +
      "[]([i_label s_min2_max2 w_chop1]cc|c " +
         "[i_label s_min2_max2 w_chop1]ddd " +
         "[i_label]([s_min2_max2 w_chop1]eee [s_min2_max2 w_chop1]fff))",
"""
aaa
        bbb
         ccc
 ddd
 eee
         fff""", 
      settings)
  }

  @Test
  fun `test wrap in the middle`() {
    val settings = CodeStyleSettings()
    settings.indentOptions!!.LABEL_INDENT_SIZE = 1
    doReformatTest(
      "[]aaa [s_min2_max2]bbb " +
      "[]" +
        "([i_label s_min2_max2]ccc " +
         "[i_label s_min2_max2 w_normal]ddd " +
         "[i_label]" +
         "([s_min2_max2]eee [s_min2_max2]|fff))",
"""
aaa  bbb  ccc
 ddd  eee  fff""", settings)
  }


  @Test
  fun `test multiple wrap`() {
    doReformatTest("[w_normal]a[w_normal]b|[w_normal]([w_normal]c)",
                   "ab\n" +
           "        c")

  }

  @Test
  fun `test nested calls`() {
    doReformatTest("[i_none]1 [i_none]([i_cont]a[i_none]([i_cont]2 [i_none]([i_cont]b[i_none]([i_cont]3\n[i_none]([i_cont]c)))))",
                   """
1 a2 b3
        c""")
  }

  @Test
  fun `test different wraps`() {
    doReformatTest(
      "[i_none w_normal]([w_normal]x[w_normal]a[w_normal]b[w_normal]x)" + 
      "[i_none w_normal]([w_normal]x[w_normal]c[w_normal]d[w_normal]x)" +
      "[i_none w_normal]([w_normal]x[w_normal]e[w_normal]|f[w_normal]x)",
      "xabxxcdx\nxefx")
  }
  
}