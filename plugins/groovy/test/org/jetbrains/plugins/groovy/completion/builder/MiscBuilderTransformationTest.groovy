// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion.builder

import com.intellij.psi.OriginInfoAwareElement
import com.intellij.psi.impl.light.LightElement
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor

@CompileStatic
class MiscBuilderTransformationTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  void 'test resolve inner class from java'() {
    myFixture.addFileToProject 'sample/Bean.groovy', '''\
package sample

@groovy.transform.builder.Builder
class Bean {
    String prop1
    Integer prop2
}
'''
    myFixture.configureByText 'JavaConsumer.java', '''\
import sample.Bean;

class JavaConsumer {
    void foo(Bean.Bean<caret>Builder b) {}
}
'''
    def resolved = file.findReferenceAt(myFixture.caretOffset)?.resolve()
    assert resolved instanceof LightElement
    assert (resolved as OriginInfoAwareElement).originInfo == BuilderAnnotationContributor.ORIGIN_INFO
  }

  void 'test find class'() {
    myFixture.addFileToProject 'sample/Bean.groovy', '''\
package sample

@groovy.transform.builder.Builder
class Bean {
    String prop1
    Integer prop2
}
'''
    myFixture.configureByText 'JavaConsumer.java', '''\
import sample.Bean;

class JavaConsumer {
    void main() {
        Bean b = Bean.builder().prop1("").prop2(1).build();
    }
}
'''
    myFixture.checkHighlighting()

    def clazz = myFixture.findClass('sample.Bean.BeanBuilder')
    assert clazz in LightElement
    assert (clazz as OriginInfoAwareElement).originInfo == BuilderAnnotationContributor.ORIGIN_INFO
  }
}
