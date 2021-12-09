// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.testFramework.RunAll
import groovy.transform.CompileStatic
import org.jetbrains.plugins.gradle.codeInspection.GradleDisablerTestUtils
import org.jetbrains.plugins.gradle.importing.highlighting.GradleHighlightingBaseTest
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.util.TypingTest
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROVIDER_PROPERTY

@CompileStatic
class GradleManagedPropertyTest extends GradleHighlightingBaseTest implements TypingTest {

  protected List<String> getParentCalls() {
    return []
  }

  private void setupProject() {
    createProjectSubFile("buildSrc/build.gradle")
    createProjectSubFile("buildSrc/src/main/java/pkg/MyExtension.java", '''\
package pkg;
import org.gradle.api.provider.Property;
public abstract class MyExtension {
    abstract Property<String> getStringProperty();
    Property<Integer> getIntegerProperty() {
      throw new RuntimeException();
    }
}
''')
    importProject('project.extensions.create("myExt", pkg.MyExtension)')
  }

  @Test
  @TargetVersions("6.0+")
  void types() {
    setupProject()
    def data = [
      '<caret>myExt'                              : 'pkg.MyExtension',
      'myExt.<caret>stringProperty'               : "$GRADLE_API_PROVIDER_PROPERTY<$JAVA_LANG_STRING>",
      'myExt.getStringProperty(<caret>)'          : "$GRADLE_API_PROVIDER_PROPERTY<$JAVA_LANG_STRING>",
      'myExt.getStringProperty().get(<caret>)'    : JAVA_LANG_STRING,
      'myExt { <caret>delegate }'                 : 'pkg.MyExtension',
      'myExt { <caret>stringProperty }'           : "$GRADLE_API_PROVIDER_PROPERTY<$JAVA_LANG_STRING>",
      'myExt { getStringProperty(<caret>) }'      : "$GRADLE_API_PROVIDER_PROPERTY<$JAVA_LANG_STRING>",
      'myExt { getStringProperty().get(<caret>) }': JAVA_LANG_STRING,
    ]
    RunAll.runAll(data) { String expression, String type ->
      doTest(expression) {
        typingTest(elementUnderCaret(GrExpression), type)
      }
    }
  }

  @Test
  @TargetVersions("6.0+")
  void highlighting() {
    setupProject()
    GradleDisablerTestUtils.enableAllDisableableInspections(testRootDisposable)
    fixture.enableInspections(GrUnresolvedAccessInspection, GroovyAssignabilityCheckInspection, GroovyAccessibilityInspection)
    updateProjectFile '''\
myExt.integerProperty = 42
<warning descr="Cannot assign 'Object' to 'Integer'">myExt.integerProperty</warning> = new Object()
myExt.setIntegerProperty(69)
myExt.setIntegerProperty<warning descr="'setIntegerProperty' in 'pkg.MyExtension' cannot be applied to '(java.lang.Object)'">(new Object())</warning>
myExt {
  integerProperty = 42
  <warning descr="Cannot assign 'Object' to 'Integer'">integerProperty</warning> = new Object()
  setIntegerProperty(69)
  setIntegerProperty<warning descr="'setIntegerProperty' in 'pkg.MyExtension' cannot be applied to '(java.lang.Object)'">(new Object())</warning>
}
'''
    fixture.checkHighlighting()
  }
}
