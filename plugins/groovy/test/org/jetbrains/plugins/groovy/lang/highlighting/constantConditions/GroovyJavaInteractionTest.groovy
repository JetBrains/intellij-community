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
package org.jetbrains.plugins.groovy.lang.highlighting.constantConditions

import com.intellij.codeInspection.dataFlow.DataFlowInspectionBase
import org.jetbrains.plugins.groovy.codeInspection.dataflow.GrConstantConditionsInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase


class GroovyJavaInteractionTest extends GrHighlightingTestBase {

  void "test java referencing final groovy field"() {
    myFixture.enableInspections new DataFlowInspectionBase()
    myFixture.addFileToProject 'GroovyClassWithField.groovy', '''
class GroovyClassWithField {
    public final field

    GroovyClassWithField() {
        field = new Object()
    }
}
'''
    myFixture.addFileToProject 'JavaClassReferencingGroovy.java', '''
public class JavaClassReferencingGroovy {
    void method(GroovyClassWithField c) {
        if (<warning descr="Condition 'c.field == null' is always 'false'">c.field == null</warning>) {}
    }
}
'''
    myFixture.testHighlighting 'JavaClassReferencingGroovy.java'
  }

  void "test groovy referencing final java field"() {
    myFixture.enableInspections new GrConstantConditionsInspection()
    myFixture.addFileToProject 'JavaClassWithFinalField.java', '''
public class JavaClassWithFinalField {
    final Object field;

    public JavaClassWithFinalField() {
        field = new Object();
    }
}
'''
    myFixture.addFileToProject 'GroovyClassReferencingJava.groovy', '''
class GroovyClassReferencingJava {
    def method(JavaClassWithFinalField c) {
        if (<warning descr="Condition 'c.field == null' is always false">c.field == null</warning>) {}
    }
}
'''
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting 'GroovyClassReferencingJava.groovy'
  }
}
