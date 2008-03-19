package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GrDynamicImplicitMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 12.02.2008
 */
public class DMethodElement extends DItemElement implements Comparable {
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

  public int compareTo(Object o) {
    if (!(o instanceof DMethodElement)) return 0;
    final DMethodElement otherMethod = (DMethodElement) o;

    return getName().compareTo(otherMethod.getName()) + getType().compareTo(otherMethod.getType());
  }
}