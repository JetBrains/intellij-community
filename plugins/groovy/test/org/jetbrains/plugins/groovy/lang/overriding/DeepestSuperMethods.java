package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;
import junit.framework.Test;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.08.2007
 */
public class DeepestSuperMethods extends OverridingTest {
  private static final String DATA_PATH = "testdata/overriding/deepestSuperMethods/";

  public DeepestSuperMethods() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }
  PsiMethod[] findMethod(PsiMethod method) {
    return method.findDeepestSuperMethods();
  }

  public static Test suite() {
    return new DeepestSuperMethods();
  }
}
