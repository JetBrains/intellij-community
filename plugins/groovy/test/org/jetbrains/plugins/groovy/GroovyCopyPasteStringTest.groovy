// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.RunAll
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.editor.GroovyLiteralCopyPasteProcessor
import org.jetbrains.plugins.groovy.util.BaseTest
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.junit.Assert
import org.junit.Test

import static org.jetbrains.plugins.groovy.lang.psi.util.StringKind.TestsOnly.*

@CompileStatic
class GroovyCopyPasteStringTest extends GroovyLatestTest implements BaseTest {

  @Test
  void 'copy'() {
    def data = [
      $/<selection>'\\'</selection>/$    : $/'\\'/$,
      $/<selection>'\\</selection>'/$    : $/'\\/$,
      $/'<selection>\\'</selection>/$    : $/\\'/$,
      $/'<selection>\\</selection>'/$    : $/\/$,

      $/<selection>'''\\'''</selection>/$: $/'''\\'''/$,
      $/'<selection>''\\'''</selection>/$: $/''\\'''/$,
      $/''<selection>'\\'''</selection>/$: $/'\\'''/$,
      $/'''<selection>\\'''</selection>/$: $/\\'''/$,
      $/<selection>'''\\</selection>'''/$: $/'''\\/$,
      $/<selection>'''\\'</selection>''/$: $/'''\\'/$,
      $/<selection>'''\\''</selection>'/$: $/'''\\''/$,
      $/'''<selection>\\</selection>'''/$: $/\/$,

      $/<selection>"\\"</selection>/$    : $/"\\"/$,
      $/<selection>"\\</selection>"/$    : $/"\\/$,
      $/"<selection>\\"</selection>/$    : $/\\"/$,
      $/"<selection>\\</selection>"/$    : $/\/$,

      $/<selection>"""\\"""</selection>/$: $/"""\\"""/$,
      $/"<selection>""\\"""</selection>/$: $/""\\"""/$,
      $/""<selection>"\\"""</selection>/$: $/"\\"""/$,
      $/"""<selection>\\"""</selection>/$: $/\\"""/$,
      $/<selection>"""\\</selection>"""/$: $/"""\\/$,
      $/<selection>"""\\"</selection>""/$: $/"""\\"/$,
      $/<selection>"""\\""</selection>"/$: $/"""\\""/$,
      $/"""<selection>\\</selection>"""/$: $/\/$,

      $/<selection>/\//</selection>/$    : $//\///$,
      $/<selection>/\/</selection>//$    : $//\//$,
      $//<selection>\//</selection>/$    : $/\///$,
      $//<selection>\/</selection>//$    : $///$,
    ]
    RunAll.runAll(data) { text, expectedCopy ->
      doCopyTest(text, expectedCopy)
    }.run()
  }

  @Test
  void 'find string kind for paste'() {
    def data = [
      // empty strings
      /'<caret>'/                   : SINGLE_QUOTED,
      /'''<caret>'''/               : TRIPLE_SINGLE_QUOTED,
      /"<caret>"/                   : DOUBLE_QUOTED,
      /"""<caret>"""/               : TRIPLE_DOUBLE_QUOTED,
      '/<caret>/'                   : null, // slashy string cannot be empty, so it's actually a comment
      '$/<caret>/$'                 : DOLLAR_SLASHY,

      // in template strings without injections
      '"<caret>hi there"'           : DOUBLE_QUOTED,
      '"hi<caret> there"'           : DOUBLE_QUOTED,
      '"hi there<caret>"'           : DOUBLE_QUOTED,
      '"""<caret>hi there"""'       : TRIPLE_DOUBLE_QUOTED,
      '"""hi<caret> there"""'       : TRIPLE_DOUBLE_QUOTED,
      '"""hi there<caret>"""'       : TRIPLE_DOUBLE_QUOTED,
      '/<caret>hi there/'           : SLASHY,
      '/hi<caret> there/'           : SLASHY,
      '/hi there<caret>/'           : SLASHY,
      '$/<caret>hi there/$'         : DOLLAR_SLASHY,
      '$/hi<caret> there/$'         : DOLLAR_SLASHY,
      '$/hi there<caret>/$'         : DOLLAR_SLASHY,

      // in template strings with injections
      '"<caret>hi there${}"'        : DOUBLE_QUOTED,
      '"hi<caret> there${}"'        : DOUBLE_QUOTED,
      '"hi there<caret>${}"'        : DOUBLE_QUOTED,
      '"${}<caret>hi there"'        : DOUBLE_QUOTED,
      '"${}hi<caret> there"'        : DOUBLE_QUOTED,
      '"${}hi there<caret>"'        : DOUBLE_QUOTED,
      '"""<caret>hi there${}"""'    : TRIPLE_DOUBLE_QUOTED,
      '"""hi<caret> there${}"""'    : TRIPLE_DOUBLE_QUOTED,
      '"""hi there<caret>${}"""'    : TRIPLE_DOUBLE_QUOTED,
      '"""${}<caret>hi there"""'    : TRIPLE_DOUBLE_QUOTED,
      '"""${}hi<caret> there"""'    : TRIPLE_DOUBLE_QUOTED,
      '"""${}hi there<caret>"""'    : TRIPLE_DOUBLE_QUOTED,
      '/<caret>hi there${}/'        : SLASHY,
      '/hi<caret> there${}/'        : SLASHY,
      '/hi there<caret>${}/'        : SLASHY,
      '/${}<caret>hi there/'        : SLASHY,
      '/${}hi<caret> there/'        : SLASHY,
      '/${}hi there<caret>/'        : SLASHY,
      '$/<caret>hi there${}/$'      : DOLLAR_SLASHY,
      '$/hi<caret> there${}/$'      : DOLLAR_SLASHY,
      '$/hi there<caret>${}/$'      : DOLLAR_SLASHY,
      '$/${}<caret>hi there/$'      : DOLLAR_SLASHY,
      '$/${}hi<caret> there/$'      : DOLLAR_SLASHY,
      '$/${}hi there<caret>/$'      : DOLLAR_SLASHY,

      // between injection and opening quote
      '"<caret>${}hi there"'        : DOUBLE_QUOTED,
      '"""<caret>${}hi there"""'    : TRIPLE_DOUBLE_QUOTED,
      '/<caret>${}hi there/'        : SLASHY,
      '$/<caret>${}hi there/$'      : DOLLAR_SLASHY,

      // between injections
      '"hi ${}<caret>${} there"'    : DOUBLE_QUOTED,
      '"""hi ${}<caret>${} there"""': TRIPLE_DOUBLE_QUOTED,
      '/hi ${}<caret>${} there/'    : SLASHY,
      '$/hi ${}<caret>${} there/$'  : DOLLAR_SLASHY,

      // between injection and closing quote
      '"hi there${}<caret>"'        : DOUBLE_QUOTED,
      '"""hi there${}<caret>"""'    : TRIPLE_DOUBLE_QUOTED,
      '/hi there${}<caret>/'        : SLASHY,
      '$/hi there${}<caret>/$'      : DOLLAR_SLASHY,

      // inside injection
      '"hi $<caret>{} there"'       : null,
      '"hi ${<caret>} there"'       : null,
      '"""hi $<caret>{} there"""'   : null,
      '"""hi ${<caret>} there"""'   : null,
      '/hi $<caret>{} there/'       : null,
      '/hi ${<caret>} there/'       : null,
      '$/hi $<caret>{} there/$'     : null,
      '$/hi ${<caret>} there/$'     : null,

      // outside of quotes
      /<caret>'hi'/                 : null,
      /'hi'<caret>/                 : null,
      /<caret>'''hi'''/             : null,
      /'<caret>''hi'''/             : null,
      /''<caret>'hi'''/             : null,
      /'''hi'''<caret>/             : null,
      /'''hi''<caret>'/             : null,
      /'''hi'<caret>''/             : null,
      /<caret>"hi"/                 : null,
      /"hi"<caret>/                 : null,
      /<caret>"""hi"""/             : null,
      /"<caret>""hi"""/             : null,
      /""<caret>"hi"""/             : null,
      /"""hi"""<caret>/             : null,
      /"""hi""<caret>"/             : null,
      /"""hi"<caret>""/             : null,
      '<caret>/hi/'                 : null,
      '/hi/<caret>'                 : null,
      '<caret>$/hi/$'               : null,
      '$<caret>/hi/$'               : null,
      '$/hi/$<caret>'               : null,
      '$/hi/<caret>$'               : null,
    ]

    RunAll.runAll(data) { text, expectedKind ->
      def file = fixture.configureByText('_.groovy', text)
      def selectionModel = fixture.editor.selectionModel
      def selectionStart = selectionModel.selectionStart
      def selectionEnd = selectionModel.selectionEnd
      def kind = GroovyLiteralCopyPasteProcessor.findStringKind(file, selectionStart, selectionEnd)
      Assert.assertEquals(text, expectedKind, kind)
    }.run()
  }

  @Test
  void 'multiline paste'() {
    def from = '<selection>hi\nthere</selection>'
    def data = [
      /'<caret>'/    : "'hi\\n' +\n        'there'",
      /'''<caret>'''/: "'''hi\nthere'''",
      /"<caret>"/    : '"hi\\n" +\n        "there"',
      /"""<caret>"""/: '"""hi\nthere"""',
      '/<caret>/'    : '/hi\nthere/',
      '$/<caret>/$'  : '$/hi\nthere/$',
    ]
    RunAll.runAll(data) { to, expected ->
      doCopyPasteTest(from, to, expected)
    }.run()
  }

  @Test
  void 'paste injection'() {
    def data = [
      ['<selection>$a</selection>', '"<caret>"', '"$a"'],
      ['"<selection>$a</selection>"', '"<caret>"', '"$a"'],
      ['<selection>${a}</selection>', '"<caret>"', '"${a}"'],
      ['"<selection>${a}</selection>"', '"<caret>"', '"${a}"'],
    ]
    RunAll.runAll(data) { List<String> entry ->
      doCopyPasteTest(entry[0], entry[1], entry[2])
    }.run()
  }

  private void doCopyTest(String text, String expectedCopy) {
    fixture.configureByText 'from.groovy', text
    fixture.performEditorAction IdeActions.ACTION_COPY
    fixture.configureByText 'to.txt', ''
    fixture.performEditorAction IdeActions.ACTION_PASTE
    fixture.checkResult expectedCopy
  }

  private void doCopyPasteTest(String fromText, String toText, String expected) {
    fixture.configureByText 'from.groovy', fromText
    fixture.performEditorAction IdeActions.ACTION_COPY
    fixture.configureByText 'to.groovy', toText
    fixture.performEditorAction IdeActions.ACTION_PASTE
    fixture.checkResult expected
  }
}
