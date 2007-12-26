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

  public void testCircular1() throws Exception {
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
}
