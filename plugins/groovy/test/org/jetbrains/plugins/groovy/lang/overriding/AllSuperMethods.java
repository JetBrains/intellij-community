package org.jetbrains.plugins.groovy.lang.overriding;

import junit.framework.Test;
import com.intellij.psi.PsiMethod;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.08.2007
 */
public class AllSuperMethods extends OverridingTest{
  private static final String DATA_PATH = "testdata/overriding/allSuperMethods/";

  public AllSuperMethods() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }
  public static Test suite() {
    return new AllSuperMethods();
  }

  PsiMethod[] findMethod(PsiMethod method) {
    return method.findSuperMethods();
  }
}
