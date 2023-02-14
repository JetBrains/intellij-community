// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class AddConstructorMatchingSuperTest extends GrIntentionTestCase {
  private static final String HINT = "Create constructor matching super"

  AddConstructorMatchingSuperTest() {
    super(HINT)
  }

  final String basePath = TestUtils.testDataPath + 'intentions/constructorMatchingSuper/'

  void testGroovyToGroovy() {
    doTextTest('''\
@interface Anno {}
class Base {
    Base(int p, @Anno int x) throws Exception {}
}

class Derived exten<caret>ds Base {
}
''', '''\
@interface Anno {}
class Base {
    Base(int p, @Anno int x) throws Exception {}
}

class Derived extends Base {
    <caret>Derived(int p, int x) throws Exception {
        super(p, x)
    }
}
''')
  }

  void testJavaToGroovy() {
    myFixture.addClass('''\
@interface Anno {}
class Base {
    Base(int p, @Anno int x) throws Exception {}
}
''')
    doTextTest('''\
class Derived exten<caret>ds Base {
}
''', '''\
class Derived extends Base {
    <caret>Derived(int p, int x) throws Exception {
        super(p, x)
    }
}
''')
  }

  void testGroovyToJava() {
    myFixture.addClass('''\
class Base {
    Base(int p, @Override int x) throws Exception {}
}
''')
    myFixture.configureByText("a.java", '''\
class Derived exten<caret>ds Base {
}
''')
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(HINT)
    myFixture.launchAction(assertOneElement(list))
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
    myFixture.checkResult('''\
class Derived extends Base {
    <caret>Derived(int p, int x) throws Exception {
        super(p, x);
    }
}
''')
  }

}
