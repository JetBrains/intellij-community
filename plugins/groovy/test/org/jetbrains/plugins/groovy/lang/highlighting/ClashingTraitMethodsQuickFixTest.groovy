/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.highlighting
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle
import org.jetbrains.plugins.groovy.codeInspection.confusing.ClashingTraitMethodsInspection
/**
 * Created by Max Medvedev on 09/06/14
 */
class ClashingTraitMethodsQuickFixTest /*extends GrIntentionTestCase*/ {
  ClashingTraitMethodsQuickFixTest() {
    super(GroovyInspectionBundle.message("declare.explicit.implementations.of.trait"), ClashingTraitMethodsInspection)
  }

  void testQuickFix() {
    doTextTest('''\
trait T1 {
    def foo(){}
}

trait T2 {
    def foo(){}
}

class <caret>A implements T1, T2 {

}
''', '''\
trait T1 {
    def foo(){}
}

trait T2 {
    def foo(){}
}

class A implements T1, T2 {

    @Override
    def foo() {
        return T2.super.foo()
    }
}
''')
  }
}
