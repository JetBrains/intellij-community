package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;
import junit.framework.Test;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.08.2007
 */
public class DeepestSuperMethodsTest extends OverridingTester {
  private static final String DATA_PATH = TestUtils.getTestDataPath() + "/overriding/deepestSuperMethods/";

  public DeepestSuperMethodsTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }
  PsiMethod[] findMethod(PsiMethod method) {
    return method.findDeepestSuperMethods();
  }

  public static Test suite() {
    return new DeepestSuperMethodsTest();
  }
}
