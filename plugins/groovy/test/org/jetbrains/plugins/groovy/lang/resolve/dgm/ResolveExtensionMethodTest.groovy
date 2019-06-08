// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.dgm

import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase

import static org.jetbrains.plugins.groovy.dgm.DGMUtil.ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE

@CompileStatic
class ResolveExtensionMethodTest extends GroovyResolveTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  private void addExtension(String directory = "services", @Language("Groovy") String text) {
    def factory = PsiFileFactory.getInstance(project)
    def file = factory.createFileFromText('a.groovy', GroovyFileType.GROOVY_FILE_TYPE, text) as GroovyFile
    def fqn = file.typeDefinitions.first().qualifiedName
    def path = fqn.split('\\.').join('/') + '.groovy'
    fixture.addFileToProject(path, text)
    fixture.addFileToProject("META-INF/$directory/$ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE", """\
moduleName=ext-module
moduleVersion=1.0
extensionClasses=$fqn
""")
  }

  void 'test prefer extension method wo conversions over class method with SAM conversion'() {
    fixture.addClass '''\
package com.foo.baz;

public interface I {
  void sam();
}
'''
    fixture.addClass '''\
package com.foo.bar;
import com.foo.baz.I;

public interface D {
  void myMethod(I i);
} 
'''
    addExtension '''\
package com.foo.bad
import com.foo.bar.D

class Exts {
  static void myMethod(D d, Closure c) {}
}
'''
    resolveByText('''
import com.foo.bar.D

void foo(D d) {
  d.my<caret>Method {}
}
''', GrGdkMethod)
  }

  void 'test resolve extension method'() {
    addExtension '''\
class StringExtensions {
  static void myMethod(String s) {}
}
'''
    resolveByText '''\
"hi".<caret>myMethod()
''', GrGdkMethod
  }

  void 'test resolve extension method from groovy directory'() {
    addExtension 'groovy', '''\
class StringExtensions {
  static void myMethod(String s) {}
}
'''
    resolveByText '''\
"hi".<caret>myMethod()
''', GrGdkMethod
  }
}
