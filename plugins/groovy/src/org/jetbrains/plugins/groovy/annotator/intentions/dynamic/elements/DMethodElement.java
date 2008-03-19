package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrDynamicImplicitMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

import java.util.ArrayList;
import java.util.List;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiManager;

/**
 * User: Dmitry.Krasilschikov
 * Date: 12.02.2008
 */
public class DMethodElement extends DItemElement {
  public List<MyPair> myPairs = new ArrayList<MyPair>();
  private PsiMethod myImplicitMethod;

  public DMethodElement() {
    super(null, null);
  }

  public DMethodElement(String name, String returnType, List<MyPair> pairs) {
    super(name, returnType);

    myPairs = pairs;
  }

  public List<MyPair> getPairs() {
    return myPairs;
  }

  public void clearCache() {
    myImplicitMethod = null;
  }

  public PsiMethod getPsi(PsiManager manager, String containingClassName) {
    if (myImplicitMethod != null) return myImplicitMethod;

    final GrMethod method = GroovyPsiElementFactory.getInstance(manager.getProject()).createMethodFromText(getName(), getType(), QuickfixUtil.getArgumentsTypes(myPairs));

    myImplicitMethod = new GrDynamicImplicitMethod(manager, method, containingClassName);
    return myImplicitMethod;
  }
}