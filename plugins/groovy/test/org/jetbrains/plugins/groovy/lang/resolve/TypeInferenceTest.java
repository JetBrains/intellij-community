/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class TypeInferenceTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "resolve/inference/";
  }

  public void testTryFinallyFlow() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("tryFinallyFlow/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertTrue(type instanceof PsiIntersectionType);
    final PsiType[] conjuncts = ((PsiIntersectionType) type).getConjuncts();
    assertEquals(conjuncts.length, 2);
  }

  public void testTryFinallyFlow1() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("tryFinallyFlow1/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertTrue(type.equalsToText("java.lang.Integer"));
  }

  public void testTryFinallyFlow2() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("tryFinallyFlow2/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertTrue(type.equalsToText("java.lang.Integer"));
  }

  public void testThrowVariable() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("throwVariable/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals("java.lang.Exception", type.getCanonicalText());
  }

  public void testGrvy852() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("grvy852/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals("java.lang.Object", type.getCanonicalText());
  }

  public void testGenericMethod() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("genericMethod/A.groovy").getElement();
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals("java.util.List<java.lang.String>", type.getCanonicalText());
  }

  public void testCircular() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("circular/A.groovy").getElement();
    assertNull(ref.getType());
  }

  public void  testCircular1() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("circular1/A.groovy").getElement();
    assertNull(ref.getType());
  }

  public void testClosure() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("closure/A.groovy").getElement();
    assertNotNull(ref.getType());
  }

  public void testClosure1() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("closure1/A.groovy").getElement();
    assertTrue(ref.getType().equalsToText("java.lang.Integer"));
  }

  public void testClosure2() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("closure2/A.groovy").getElement();
    assertTrue(ref.getType().equalsToText("java.lang.Integer"));
  }

  public void testGrvy1209() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("grvy1209/A.groovy").getElement();
    assertTrue(ref.getType().equalsToText("java.lang.String"));
  }

  public void testLeastUpperBoundClosureType() throws Exception {
    GrReferenceExpression ref= (GrReferenceExpression)configureByFile("leastUpperBoundClosureType/A.groovy").getElement();
    assertInstanceOf(ref.getType(), GrClosureType.class);
  }

  public void testJavaLangClassType() throws Exception {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("javaLangClassType/A.groovy").getElement();
    assertEquals("java.lang.String", ref.getType().getCanonicalText());
  }

  public void testGenericWildcard() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("genericWildcard/A.groovy").getElement();
    assertEquals("A<Base>", ref.getType().getCanonicalText());
  }

  public void testArrayLikeAccessWithIntSequence() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("arrayLikeAccessWithIntSequence/A.groovy").getElement();
    assertEquals("java.util.List", ref.getType().getCanonicalText());
  }

  public void testArrayAccess() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("arrayAccess/A.groovy");
    assertEquals(CommonClassNames.JAVA_LANG_STRING, ref.getType().getCanonicalText());
  }

  public void testReturnTypeByTailExpression() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("returnTypeByTailExpression/A.groovy");
    assertEquals(CommonClassNames.JAVA_LANG_STRING, ref.getType().getCanonicalText());
  }
}
