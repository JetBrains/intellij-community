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
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiPolyVariantReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

/**
 * @author Max Medvedev
 */
class ResolveAnnotationAttributeTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return null
  }

  void testSimpleAttribute() {
    final ref = this.configureByText('''
@interface A {
  String foo()
}

@A(f<caret>oo = '3')
class A {}
''') as PsiPolyVariantReference

    assertNotNull(ref.resolve())
  }

  void testAliasAttribute() {
    final ref = this.configureByText('''
@interface A {
  String foo()
}

@groovy.transform.AnnotationCollector([A])
@interface Alias {
  String foo()
}

@Alias(f<caret>oo = '3')
class X {}
''') as PsiPolyVariantReference

    final resolved = ref.resolve()
    assertNotNull(resolved)
    assertInstanceOf(resolved, GrMethod)
    assertEquals('A', resolved.containingClass.qualifiedName)
  }

  void testMultiAliasAttribute() {
    final ref = this.configureByText('''
@interface A {
  String foo()
}
@interface B {
  String foo()
}

@groovy.transform.AnnotationCollector([A, B])
@interface Alias {
  String foo()
}

@Alias(f<caret>oo = '3')
class X {}
''') as PsiPolyVariantReference

    final resolved = ref.resolve()
    assertNull(resolved)

    for (GroovyResolveResult result : ref.multiResolve(false)) {
      final r = result.element

      assertInstanceOf(r, GrAnnotationMethod)
      assertTrue('Alias' != r.containingClass.qualifiedName)

    }
  }
}
