/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions

/**
 * @author Max Medvedev
 */
class GrCreateConstructorMatchingSuperTest extends GrIntentionTestCase {
  GrCreateConstructorMatchingSuperTest() {
    super('Create constructor matching super')
  }

  public void testSuperWithJavaDoc() throws Exception {
    myFixture.addClass('''\
public class Base {
    /**
     * my doc
     */
     public Base(int x) {
     }
}
''')

    doTextTest('''\
class <caret>Inheritor extends Base {
}
''', '''\
class Inheritor extends Base {
    /**
     * my doc
     */
    Inheritor(int x) {
        super(x)
    }
}
''')

  }

  void testInvalidOriginalParameterName() {
    myFixture.addClass('''\
class Base {
  Base(String in, String s) {
  }
}''')

    doTextTest('''\
class <caret>Inh extends Base {
}
''', '''\
class Inh extends Base {
    def Inh(String s, String s2) {
        super(s, s2)
    }
}
''')
  }
}
