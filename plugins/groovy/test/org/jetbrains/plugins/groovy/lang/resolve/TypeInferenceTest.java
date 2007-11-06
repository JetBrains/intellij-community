package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class TypeInferenceTest extends GroovyResolveTestCase {
  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/inference/";
  }

  public void testTryFinallyFlow() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("tryFinallyFlow/A.groovy");
    final PsiType type = ref.getType();
    assertTrue(type instanceof PsiIntersectionType);
    final PsiType[] conjuncts = ((PsiIntersectionType) type).getConjuncts();
    assertEquals(conjuncts.length, 2);
  }

  public void testTryFinallyFlow1() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("tryFinallyFlow1/A.groovy");
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertTrue(type.equalsToText("java.lang.Integer"));
  }

  public void testTryFinallyFlow2() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("tryFinallyFlow2/A.groovy");
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertTrue(type.equalsToText("java.lang.Integer"));
  }

  public void testThrowVariable() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("throwVariable/A.groovy");
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals("java.lang.Exception", type.getCanonicalText());
  }

  public void testGenericMethod() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("genericMethod/A.groovy");
    final PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals("java.util.List<java.lang.String>", type.getCanonicalText());
  }

  public void testCircular() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("circular/A.groovy");
    assertNull(ref.getType());
  }

  public void testClosure() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("closure/A.groovy");
    assertNotNull(ref.getType());
  }

  public void testClosure1() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("closure1/A.groovy");
    assertTrue(ref.getType().equalsToText("java.lang.Integer"));
  }
}
