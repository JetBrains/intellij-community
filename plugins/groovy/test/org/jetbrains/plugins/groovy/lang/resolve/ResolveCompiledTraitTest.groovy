/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitField
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod
import org.jetbrains.plugins.groovy.util.TestUtils

import static org.jetbrains.plugins.groovy.config.GroovyFacetUtil.getBundledGroovyJar

@CompileStatic
class ResolveCompiledTraitTest extends GroovyResolveTestCase {

  final String basePath = "resolve/"

  final LightProjectDescriptor projectDescriptor = new GroovyLightProjectDescriptor(bundledGroovyJar as String) {
    @Override
    void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry)
      PsiTestUtil.addLibrary(module, model, "some-library", "${TestUtils.absoluteTestDataPath}/lib", 'some-library.jar');
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