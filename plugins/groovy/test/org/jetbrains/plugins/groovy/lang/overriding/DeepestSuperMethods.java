package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.psi.PsiMethod;
import com.intellij.ide.util.MemberChooser;
import junit.framework.Test;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrMethodCallImpl;

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
