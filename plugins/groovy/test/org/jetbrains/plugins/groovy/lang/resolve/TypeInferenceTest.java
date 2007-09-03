package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.util.TestUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiType;

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

}
