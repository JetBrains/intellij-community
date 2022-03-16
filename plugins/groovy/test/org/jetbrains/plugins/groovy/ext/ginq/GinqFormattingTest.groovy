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
    GinqTestUtils.setUp(myFixture)
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
}
