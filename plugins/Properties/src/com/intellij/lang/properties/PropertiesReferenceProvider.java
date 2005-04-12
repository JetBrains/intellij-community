package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
class PropertiesReferenceProvider implements PsiReferenceProvider {
  public PsiReference[] getReferencesByElement(PsiElement element) {
    PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
    String text = (String)literalExpression.getValue();
    //PsiClass aClass = element.getManager().findClass(text, GlobalSearchScope.allScope(element.getProject()));
    //return new PsiReference[]{new LightClassReference(element.getManager(), element.getText(), aClass)};
    List<Property> properties = PropertiesUtil.findPropertiesByKey(element.getProject(), text);
    List<PsiReference> references = new ArrayList<PsiReference>(properties.size());
    for (int i = 0; i < properties.size(); i++) {
      Property property = properties.get(i);
      PsiReference reference = new PropertyReference(property, literalExpression);
      references.add(reference);
    }
    return references.toArray(new PsiReference[references.size()]);
  }

  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }

  private static class PropertyReference implements PsiReference {
    private final Property myProperty;
    private final PsiLiteralExpression myLiteralExpression;

    public PropertyReference(final Property property, final PsiLiteralExpression literalExpression) {
      myProperty = property;
      myLiteralExpression = literalExpression;
    }

    public PsiElement getElement() {
      return myLiteralExpression;
    }

    public TextRange getRangeInElement() {
      return new TextRange(1,myLiteralExpression.getTextLength()-1);
    }

    public PsiElement resolve() {
      return myProperty;
    }

    public String getCanonicalText() {
      return myProperty.getName();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      throw new IncorrectOperationException("not implemented");
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      throw new IncorrectOperationException("not implemented");
    }

    public boolean isReferenceTo(PsiElement element) {
      return element == myProperty;
    }

    public Object[] getVariants() {
      return new Object[0];
    }

    public boolean isSoft() {
      return false;
    }
  }
}
