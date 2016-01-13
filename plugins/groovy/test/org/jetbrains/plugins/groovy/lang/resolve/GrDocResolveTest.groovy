/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GrDocResolveTest extends GroovyResolveTestCase {
  final String basePath = null

  void 'test resolve to property from within class'() {
    resolveByText('''\
class Doc {
  static String C = "123"
  /**
   * {@link #<caret>C}
   */
  String f() {}
}
''', GrField)
  }

  void 'test resolve to method from within class'() {
    resolveByText('''\
class Doc {
  /**
   * {@link #<caret>f}
   */
  static String C = "123"
  String f() {}
}
''', GrMethod)
  }

  void 'test resolve to property from class comment'() {
    resolveByText('''\
/**
 * {@link #<caret>C}
 */
class Doc {
  static String C = "123"

  String f() {}
}
''', GrField)
  }

  void 'test resolve to method from class comment'() {
    resolveByText('''\
/**
 * {@link #<caret>f}
 */
class Doc {
  static String C = "123"
  String f() {}
}
''', GrMethod)
  }
}
