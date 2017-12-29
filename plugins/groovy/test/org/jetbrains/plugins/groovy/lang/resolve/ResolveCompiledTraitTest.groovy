/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitField
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod
import org.jetbrains.plugins.groovy.util.TestUtils
import org.jetbrains.plugins.groovy.util.ThrowingDecompiler

import static org.jetbrains.plugins.groovy.config.GroovyFacetUtil.getBundledGroovyJar

@CompileStatic
class ResolveCompiledTraitTest extends GroovyResolveTestCase {

  final String basePath = "resolve/"

  final LightProjectDescriptor projectDescriptor = new GroovyLightProjectDescriptor(bundledGroovyJar as String) {
    @Override
    void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry)
      model.moduleLibraryTable.createLibrary("some-library").modifiableModel.with {
        def fs = JarFileSystem.instance
        def root = "${TestUtils.absoluteTestDataPath}/lib"
        addRoot(fs.refreshAndFindFileByPath("$root/some-library.jar!/"), OrderRootType.CLASSES)
        addRoot(fs.refreshAndFindFileByPath("$root/some-library-src.jar!/"), OrderRootType.SOURCES)
        commit()
      }
    }
  }

  void 'test resolve trait'() {
    resolveByText '''\
class ExternalConcrete implements somepackage.T<caret>T {
    @Override
    def someAbstractMethod() {}
    @Override
    void anotherInterfaceMethod() {}
}
''', ClsClassImpl
  }

  void 'test method implemented in trait()'() {
    resolveByText '''\
class ExternalConcrete implements somepackage.TT {
    @Override
    def someAbstractMethod() {}
    @Override
    void anotherInterfaceMethod() {}
}
new ExternalConcrete().som<caret>eMethod()
''', GrTraitMethod
  }

  void 'test method from interface implemented in trait'() {
    resolveByText '''\
class ExternalConcrete implements somepackage.TT {
    @Override
    def someAbstractMethod() {}
    @Override
    void anotherInterfaceMethod() {}
}
new ExternalConcrete().interface<caret>Method()
''', GrTraitMethod
  }

  void 'test static trait method'() {
    resolveByText '''\
class ExternalConcrete implements somepackage.TT {
    @Override
    def someAbstractMethod() {}
    @Override
    void anotherInterfaceMethod() {}
}
ExternalConcrete.someStatic<caret>Method()
''', GrTraitMethod
  }

  void 'test trait field'() {
    resolveByText '''\
class ExternalConcrete implements somepackage.TT {
    @Override
    def someAbstractMethod() {}
    @Override
    void anotherInterfaceMethod() {}
}
ExternalConcrete.some<caret>Field
''', GrTraitMethod
  }

  void 'test static trait field'() {
    resolveByText '''\
class ExternalConcrete implements somepackage.TT {
    @Override
    def someAbstractMethod() {}
    @Override
    void anotherInterfaceMethod() {}
}
ExternalConcrete.someStatic<caret>Field
''', GrTraitMethod
  }

  void 'test trait field full name'() {
    resolveByText '''\
class ExternalConcrete implements somepackage.TT {
    @Override
    def someAbstractMethod() {}
    @Override
    void anotherInterfaceMethod() {}
}
ExternalConcrete.somepackage_<caret>TT__someField
''', GrTraitField
  }

  void 'test static trait field full name'() {
    resolveByText '''\
class ExternalConcrete implements somepackage.TT {
    @Override
    def someAbstractMethod() {}
    @Override
    void anotherInterfaceMethod() {}
}
ExternalConcrete.somepackage_TT_<caret>_someStaticField
''', GrTraitField
  }

  void 'test highlighting no errors'() {
    testHighlighting '''
class ExternalConcrete implements somepackage.TT {
    @Override
    def someAbstractMethod() {}
    @Override
    void anotherInterfaceMethod() {}
}
'''
  }

  void 'test compiled trait no errors'() {
    testHighlighting '''
class ExternalConcrete implements somepackage.TT {
    @Override
    def someAbstractMethod() {}
    @Override
    void anotherInterfaceMethod() {}
}'''
  }

  void 'test not implemented compiled trait method'() {
    testHighlighting '''
<error descr="Method 'someAbstractMethod' is not implemented">class ExternalConcrete implements somepackage.TT</error> {
    @Override
    void anotherInterfaceMethod() {}
}'''
  }

  void 'test not implemented compiled interface method with trait in hierarchy'() {
    testHighlighting '''
<error descr="Method 'anotherInterfaceMethod' is not implemented">class ExternalConcrete implements somepackage.TT</error> {
    @Override
    def someAbstractMethod() {}
}'''
  }

  void 'test trait parameter not within its bounds'() {
    testHighlighting '''\
class ExternalConcrete2 implements somepackage.GenericTrait<String, somepackage.Pojo, <warning descr="Type parameter 'java.lang.Integer' is not in its bound; should extend 'A'">Integer</warning>> {}
'''
  }

  void 'test generic trait method'() {
    def definition = configureTraitInheritor()
    def method = definition.findMethodsByName("methodWithTraitGenerics", false)[0]
    assert method.typeParameters.length == 0
    assert method.returnType.canonicalText == "Pojo"
    assert method.parameterList.parametersCount == 2
    assert method.parameterList.parameters[0].type.canonicalText == "java.lang.String"
    assert method.parameterList.parameters[1].type.canonicalText == "PojoInheritor"
  }

  void 'test generic trait method with type parameters'() {
    configureTraitInheritor()
    def reference = configureByText(
      'foo.groovy',
      'new ExternalConcrete().<Integer>methodWit<caret>hMethodGenerics(1, "2", null)',
      GrReferenceExpression
    )
    def resolved = reference.advancedResolve()
    def method = resolved.element as PsiMethod
    def substitutor = resolved.substitutor
    assert method.typeParameterList.typeParameters.length == 1
    assert substitutor.substitute(method.returnType).canonicalText == "java.lang.Integer"
    assert method.parameterList.parametersCount == 3
    assert substitutor.substitute(method.parameterList.parameters[0].type).canonicalText == "java.lang.Integer"
    assert method.parameterList.parameters[1].type.canonicalText == "java.lang.String"
    assert method.parameterList.parameters[2].type.canonicalText == "PojoInheritor"
  }

  void 'test generic trait method type parameters clashing'() {
    configureTraitInheritor()
    def reference = configureByText(
      'foo.groovy',
      'new ExternalConcrete().<Integer>methodWith<caret>MethodGenericsClashing(1,"", new PojoInheritor())',
      GrReferenceExpression
    )
    def resolved = reference.advancedResolve()
    def method = resolved.element as PsiMethod
    def substitutor = resolved.substitutor
    assert method.typeParameterList.typeParameters.length == 1
    assert substitutor.substitute(method.returnType).canonicalText == "java.lang.Integer"
    assert method.parameterList.parametersCount == 3
    assert substitutor.substitute(method.parameterList.parameters[0].type).canonicalText == "java.lang.Integer"
    assert method.parameterList.parameters[1].type.canonicalText == "java.lang.String"
    assert method.parameterList.parameters[2].type.canonicalText == "PojoInheritor"
  }

  void 'test generic trait static method'() {
    def definition = configureTraitInheritor()
    def method = definition.findMethodsByName("staticMethodWithTraitGenerics", false)[0]
    assert method.typeParameters.length == 0
    assert method.returnType.canonicalText == "Pojo"
    assert method.parameterList.parametersCount == 2
    assert method.parameterList.parameters[0].type.canonicalText == "java.lang.String"
    assert method.parameterList.parameters[1].type.canonicalText == "PojoInheritor"
  }

  void 'test generic trait static method with type parameters'() {
    configureTraitInheritor()
    def reference = configureByText(
      'foo.groovy',
      'new ExternalConcrete().<Integer>staticMethodWit<caret>hMethodGenerics(1, "2", null)',
      GrReferenceExpression
    )
    def resolved = reference.advancedResolve()
    def method = resolved.element as PsiMethod
    def substitutor = resolved.substitutor
    assert method.typeParameterList.typeParameters.length == 1
    assert substitutor.substitute(method.returnType).canonicalText == "java.lang.Integer"
    assert method.parameterList.parametersCount == 3
    assert substitutor.substitute(method.parameterList.parameters[0].type).canonicalText == "java.lang.Integer"
    assert method.parameterList.parameters[1].type.canonicalText == "java.lang.String"
    assert method.parameterList.parameters[2].type.canonicalText == "PojoInheritor"
  }

  void 'test generic trait static method type parameters clashing'() {
    configureTraitInheritor()
    def reference = configureByText(
      'foo.groovy',
      'new ExternalConcrete().<Integer>staticMethodWith<caret>MethodGenericsClashing(1, "", new PojoInheritor())',
      GrReferenceExpression
    )
    def resolved = reference.advancedResolve()
    def method = resolved.element as PsiMethod
    def substitutor = resolved.substitutor
    assert method.typeParameterList.typeParameters.length == 1
    assert substitutor.substitute(method.returnType).canonicalText == "java.lang.Integer"
    assert method.parameterList.parametersCount == 3
    assert substitutor.substitute(method.parameterList.parameters[0].type).canonicalText == "java.lang.Integer"
    assert method.parameterList.parameters[1].type.canonicalText == "java.lang.String"
    assert method.parameterList.parameters[2].type.canonicalText == "PojoInheritor"
  }

  void 'test do not resolve private trait method'() {
    fixture.enableInspections GrUnresolvedAccessInspection, GroovyAccessibilityInspection
    testHighlighting '''\
import privateTraitMethods.C 
import privateTraitMethods.T

def foo(T t) {
  t.<warning descr="Cannot resolve symbol 'privateMethod'">privateMethod</warning>() // via interface
}

new C().<warning descr="Cannot resolve symbol 'privateMethod'">privateMethod</warning>() // via implementation
'''
  }

  void 'test do not get mirror in completion'() {
    ThrowingDecompiler.disableDecompilers(testRootDisposable)
    fixture.configureByText '_.groovy', '''\
class CC implements somepackage.TT {
  def foo() {
    someMet<caret>
  }
}
'''
    fixture.completeBasic()
    assertContainsElements(
      fixture.lookupElementStrings,
      "someMethod", "someAbstractMethod", "someStaticMethod"
    )
  }

  private PsiClass configureTraitInheritor() {
    myFixture.addFileToProject "inheritors.groovy", '''\
class PojoInheritor extends somepackage.Pojo {}
class ExternalConcrete implements somepackage.GenericTrait<Pojo, String, PojoInheritor> {}
'''
    myFixture.findClass("ExternalConcrete")
  }

  private testHighlighting(String text) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text)
    myFixture.testHighlighting(true, false, true)
  }
}