// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterTestCase

class GinqFormattingTest extends GroovyFormatterTestCase {

  final LightProjectDescriptor projectDescriptor = GinqTestUtils.projectDescriptor

  @Override
  void setUp() throws Exception {
    super.setUp()
  }

  void doEnterTest(String before, String after) throws Throwable {
    myFixture.configureByText("a.groovy", before)
    myFixture.type('\n' as char)
    myFixture.checkResult(after, true)
  }

  void testBasicFragmentFormatting() {
    checkFormatting('''\
GQ {
  from x in [1]
    select x
}
''', '''\
GQ {
  from x in [1]
  select x
}
''')
  }

  void testBasicFragmentFormatting2() {
    checkFormatting('''\
GQ {
from x in [1]
select x
}
''', '''\
GQ {
  from x in [1]
  select x
}
''')
  }

  void testBasicFragmentFormatting3() {
    checkFormatting('''\
GQ {
    from x in [1]
    select x
}
''', '''\
GQ {
  from x in [1]
  select x
}
''')
  }

  void testOn() {
    groovyCustomSettings.GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.DO_NOT_WRAP
    checkFormatting('''\
GQ {
  from x in [1]
  join y in [2] 
    on x == y
  select x
}
''', '''\
GQ {
  from x in [1]
  join y in [2] on x == y
  select x
}
''')
  }

  void testOn2() {
    groovyCustomSettings.GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.WRAP_ALWAYS
    checkFormatting('''\
GQ {
  from x in [1]
  join y in [2] on x == y
  select x
}
''', '''\
GQ {
  from x in [1]
  join y in [2]
    on x == y
  select x
}
''')
  }

  void testOn3() {
    groovyCustomSettings.GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED
    checkFormatting('''\
GQ {
  from x in [1]
  join y in [2] on x == y
  select x
}
''', '''\
GQ {
  from x in [1]
  join y in [2] on x == y
  select x
}
''')
  }

  void testOn4() {
    groovyCustomSettings.GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED
    checkFormatting('''\
GQ {
  from x in [1]
  join y in [2]
    on x == y
  select x
}
''', '''\
GQ {
  from x in [1]
  join y in [2]
    on x == y
  select x
}
''')
  }

  void testHaving() {
    checkFormatting('''\
GQ {
  from x in [1]
  groupby x
      having x == y
  select x
}
''', '''\
GQ {
  from x in [1]
  groupby x
    having x == y
  select x
}
''')
  }

  void testFormattingForUntransformed() {
    checkFormatting('''\
GQ {
  from x in [1]
  where x == y
                        && y == y
  select x
}
''', '''\
GQ {
  from x in [1]
  where x == y
      && y == y
  select x
}
''')
  }

  void testUntransformedInFrom() {
    checkFormatting('''\
GQ {
  from x in [1     ]
  select x
}
''', '''\
GQ {
  from x in [1]
  select x
}
''')
  }

  void testUntransformedInJoin() {
    checkFormatting('''\
GQ {
  from x in [1]
  join y in [2     ]
  select x
}
''', '''\
GQ {
  from x in [1]
  join y in [2]
  select x
}
''')
  }

  void testUntransformedInOn() {
    checkFormatting('''\
GQ {
  from x in [1]
  join y in [2     ]
  select x
}
''', '''\
GQ {
  from x in [1]
  join y in [2]
  select x
}
''')
  }

  void testSpaceAfterCall() {
    checkFormatting('''\
GQ {
  from x in [1]
  select(x)
}
''', '''\
GQ {
  from x in [1]
  select (x)
}
''')
  }

  void testNoSpaceAfterCall() {
    groovyCustomSettings.GINQ_SPACE_AFTER_KEYWORD = false
    checkFormatting('''\
GQ {
  from x in [1]
  select(x)
}
''', '''\
GQ {
  from x in [1]
  select(x)
}
''')
  }

  void testWrapGinqFragments() {
    groovyCustomSettings.GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.WRAP_ALWAYS
    checkFormatting('''\
GQ {
  from x in [1] select x
}
''', '''\
GQ {
  from x in [1]
  select x
}
''')
  }

  void testWrapGinqFragments2() {
    groovyCustomSettings.GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.DO_NOT_WRAP
    checkFormatting('''\
GQ {
  from x in [1] 
  select x
}
''', '''\
GQ {
  from x in [1] select x
}
''')
  }

  void testWrapGinqFragments3() {
    groovyCustomSettings.GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED
    checkFormatting('''\
GQ {
  from x in [1]
  select x
}
''', '''\
GQ {
  from x in [1]
  select x
}
''')
  }

  void testWrapGinqFragments4() {
    groovyCustomSettings.GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED
    checkFormatting('''\
GQ {
  from x in [1] select x
}
''', '''\
GQ {
  from x in [1] select x
}
''')
  }

  void testBlankLines() {
    checkFormatting('''\
GQ {
  from x in [1]

  select x
}
''', '''\
GQ {
  from x in [1]

  select x
}
''')
  }

  void testFormatNestedGinq() {
    checkFormatting('''\
GQ {
  from x in (from y in [2] select y)
  select x
}
''', '''\
GQ {
  from x in (from y in [2]
             select y)
  select x
}
''')
  }

  void testEnter() {
    doEnterTest('''\
GQ {
  from x in [1]<caret>
  select x
}
''', '''\
GQ {
  from x in [1]
  <caret>
  select x
}
''')
  }

  void testEnter2() {
    doEnterTest('''\
GQ {
  from x in (from y in [2]<caret>
             select y)
  select x
}
''', '''\
GQ {
  from x in (from y in [2]
             <caret>
             select y)
  select x
}
''')
  }

  void testEnter3() {
    doEnterTest('''\
GQ {<caret>
  from x in [1]
  select x
}
''', '''\
GQ {
  <caret>
  from x in [1]
  select x
}
''')
  }

  void testIndentOn() {
    groovyCustomSettings.GINQ_INDENT_ON_CLAUSE = false
    checkFormatting('''\
GQ {
  from x in [1]
  join y in [2]
  on x == y
  select x
}
''', '''\
GQ {
  from x in [1]
  join y in [2]
  on x == y
  select x
}
''')
  }

  void testIndentHaving() {
    groovyCustomSettings.GINQ_INDENT_HAVING_CLAUSE = false
    checkFormatting('''\
GQ {
  from x in [1]
  groupby x
  having x == x
  select x
}
''', '''\
GQ {
  from x in [1]
  groupby x
  having x == x
  select x
}
''')
  }

  void testIncorrectGinq() {
    checkFormatting('''\
GQ {
    from x in [1]
    orderby x in
    limit 1, 2
    select x
}
''', '''\
GQ {
  from x in [1]
  orderby x in
      limit 1, 2
  select x
}
''')
  }

  void testNested() {
    checkFormatting('''\
def foo() {
  GQ {
    from x in [1]
    select x
  }
}
''', '''\
def foo() {
  GQ {
    from x in [1]
    select x
  }
}
''')
  }

  void testNested2() {
    checkFormatting('''\
def baz() {
  def foo() {
    GQ {
      from x in [1]
      select x
    }
  }
}
''', '''\
def baz() {
  def foo() {
    GQ {
      from x in [1]
      select x
    }
  }
}
''')
  }

  void testMethodGinq1() {
    checkFormatting('''\
import groovy.ginq.transform.GQ

@GQ
def foo() {
    from x in [1]
    select x
}
''', '''\
import groovy.ginq.transform.GQ

@GQ
def foo() {
  from x in [1]
  select x
}
''')
  }

  void testMethodFormatNestedGinq() {
    checkFormatting('''\
import groovy.ginq.transform.GQ

@GQ
def foo() {
  from x in (from y in [2] select y)
  select x
}
''', '''\
import groovy.ginq.transform.GQ

@GQ
def foo() {
  from x in (from y in [2]
             select y)
  select x
}
''')
  }

  void testMethodIncorrectGinq() {
    checkFormatting('''\
import groovy.ginq.transform.GQ

@GQ
def foo() {
    from x in [1]
    orderby x in
    limit 1, 2
    select x
}
''', '''\
import groovy.ginq.transform.GQ

@GQ
def foo() {
  from x in [1]
  orderby x in
      limit 1, 2
  select x
}
''')
  }
}
