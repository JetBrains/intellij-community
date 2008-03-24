package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiClassType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrDynamicImplicitMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 12.02.2008
 */
public class DMethodElement extends DItemElement implements Comparable {
  public List<MyPair> myPairs = new ArrayList<MyPair>();
  private PsiMethod myImplicitMethod;
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement");

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

    final String type = getType();

    try {
      final GrTypeElement typeElement = GroovyPsiElementFactory.getInstance(manager.getProject()).createTypeElement(type);

      final PsiType psiType = typeElement.getType();
      if (!(psiType instanceof PsiClassType)) return null;

      final GrMethod method = GroovyPsiElementFactory.getInstance(manager.getProject()).createMethodFromText(getName(), type, QuickfixUtil.getArgumentsTypes(myPairs));

      myImplicitMethod = new GrDynamicImplicitMethod(manager, method, containingClassName);
    } catch (IncorrectOperationException e) {
      LOG.error("expected variable declaration");
    }
    return myImplicitMethod;
  }

  public int compareTo(Object o) {
    if (!(o instanceof DMethodElement)) return 0;
    final DMethodElement otherMethod = (DMethodElement) o;

    return getName().compareTo(otherMethod.getName()) + getType().compareTo(otherMethod.getType());
  }
}