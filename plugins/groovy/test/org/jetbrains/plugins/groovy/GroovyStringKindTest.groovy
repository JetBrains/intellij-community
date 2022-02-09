// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.util.StringKind
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.Test

import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.*
import static org.junit.Assert.assertEquals

@CompileStatic
class GroovyStringKindTest extends GroovyLatestTest {

  private static void doEscapeTests(StringKind kind, Map<String, String> data) {
    TestUtils.runAll(data) { unescaped, expectedEscaped ->
      assertEquals(expectedEscaped, kind.escape(unescaped))
    }
  }

  @Test
  void 'escape single quoted'() {
    doEscapeTests SINGLE_QUOTED, [
      '\n'  : /\n/,
      '\r'  : /\r/,
      '\b'  : /\b/,
      '\t'  : /\t/,
      '\f'  : /\f/,
      'a\\b': /a\\b/,
      '5/6' : '5/6',
      '$'   : '$',
      '\''  : /\'/,
      '"'   : '"',
    ]
  }

  @Test
  void 'escape triple single quoted'() {
    doEscapeTests TRIPLE_SINGLE_QUOTED, [
      '\n'     : '\n',
//      '\r'     : /\r/,
      '\b'     : /\b/,
      '\t'     : /\t/,
      '\f'     : /\f/,
      'a\\b'   : /a\\b/,
      '5/6'    : '5/6',
      '$'      : '$',
      '"'      : '"',
      '""'     : '""',
      '"""'    : '"""',
      '""""'   : '""""',
      "'"      : /\'/,
//      "''"     : /'\'/,
//      "'''"    : /''\'/,
//      "''''"   : /''\'\'/,
//      "'''''"  : /''\''\'/,
//      "''''''" : /''\'''\'/,
      "'a"     : /'a/,
      "''a"    : /''a/,
//      "'''a"   : /''\'a/,
//      "''''a"  : /''\''a/,
//      "'''''a" : /''\'''a/,
//      "''''''a": /''\'''\'a/,
    ]
  }

  @Test
  void 'escape double quoted'() {
    doEscapeTests DOUBLE_QUOTED, [
      "\n"  : /\n/,
      "\r"  : /\r/,
      "\b"  : /\b/,
      "\t"  : /\t/,
      "\f"  : /\f/,
      "a\\b": /a\\b/,
      "5/6" : "5/6",
      "\$"  : /\$/,
      "'"   : "'",
      "\""  : /\"/,
    ]
  }

  @Test
  void 'escape triple double quoted'() {
    doEscapeTests TRIPLE_DOUBLE_QUOTED, [
      "\n"     : "\n",
//      "\r"     : /\r/,
      "\b"     : /\b/,
      "\t"     : /\t/,
      "\f"     : /\f/,
      "a\\b"   : /a\\b/,
      "5/6"    : "5/6",
      "\$"     : /\$/,
      "'"      : "'",
      "''"     : "''",
      "'''"    : "'''",
      "''''"   : "''''",
//      '"'      : /\"/,
//      '""'     : /"\"/,
//      '"""'    : /""\"/,
//      '""""'   : /""\"\"/,
//      '"""""'  : /""\""\"/,
//      '""""""' : /""\"""\"/,
      '"a'     : /"a/,
      '""a'    : /""a/,
//      '"""a'   : /""\"a/,
//      '""""a'  : /""\""a/,
//      '"""""a' : /""\"""a/,
//      '""""""a': /""\"""\"a/,
    ]
  }

  @Test
  void 'escape slashy'() {
    doEscapeTests SLASHY, [
      '\n'  : '\n',
      '\r'  : '\\u000D',
      '\b'  : '\\u0008',
      '\t'  : '\\u0009',
      '\f'  : '\\u000C',
      /a\b/ : /a\b/,
      /5\/6/: '5\\/6',
      '$'   : '\\u0024',
      /'/   : /'/,
      /"/   : /"/,
    ]
  }

  @Test
  void 'escape dollar slashy'() {
    doEscapeTests DOLLAR_SLASHY, [
      '\n'            : '\n',
      '\r'            : '\\u000D',
      '\b'            : '\\u0008',
      '\t'            : '\\u0009',
      '\f'            : '\\u000C',
      /a\b/           : /a\b/,
      $/5/6/$         : $/5/6/$,
      //$/$$/$          : /$$/, //caused compilation error in Groovy 3.0.9 because of Groovy bug (https://issues.apache.org/jira/projects/GROOVY/issues/GROOVY-10406)
      $/'/$           : $/'/$,
      $/"/$           : $/"/$,
      '$_'           : '$$_',
      'hello $ world' : 'hello $ world',
      'hello / world' : 'hello / world',
      'hello $/ world': 'hello $$/ world',
      'hello $$ world': 'hello $$$ world',
      'hello /$ world': 'hello $/$ world'
    ]
  }
}
