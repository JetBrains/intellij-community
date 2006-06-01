package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntMacroDef;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;

public class AntElementNameReference extends AntGenericReference {

  private PsiElement myResolvedElement;

  public AntElementNameReference(final GenericReferenceProvider provider, final AntStructuredElement element) {
    super(provider, element);
  }

  public AntElementNameReference(final GenericReferenceProvider provider, final AntStructuredElement element, final XmlAttribute attr) {
    super(provider, element, attr);
  }

  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntStructuredElement element = getElement();
    final AntTypeDefinition typeDef = element.getTypeDefinition();
    if (typeDef == null) return element;

    if (!(element instanceof AntTask)) {
      final AntStructuredElement definingElement = (AntStructuredElement)typeDef.getDefiningElement();
      if (definingElement != null && definingElement.getParent()instanceof AntMacroDef &&
          "element".equals(definingElement.getSourceElement().getName())) {
        // renaming macrodef's nested element
        element.getSourceElement().setName(newElementName);
      }
    }
    else {
      AntTask task = (AntTask)element;
      if (task.isMacroDefined()) {
        final XmlAttribute attr = getAttribute();
        if (attr == null) {
          // renaming macrodef itself
          task.getSourceElement().setName(newElementName);
        }
        else {
          attr.setName(newElementName);
        }
      }
    }

    return element;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof AntStructuredElement) {
      return handleElementRename(((AntStructuredElement)element).getName());
    }
    return getElement();
  }

  public PsiElement resolve() {
    if (myResolvedElement != null) return myResolvedElement;
    final AntStructuredElement element = getElement();
    final AntTypeDefinition elementDef = element.getTypeDefinition();
    if (elementDef != null) {
      if (!(element instanceof AntTask)) {
        final PsiElement nestedMacroElement = elementDef.getDefiningElement();
        return myResolvedElement = (nestedMacroElement == null) ? findClass(elementDef, element) : nestedMacroElement;
      }
      AntTask task = (AntTask)element;
      if (task.isMacroDefined()) {
        final PsiElement macrodef = elementDef.getDefiningElement();
        final XmlAttribute attr = getAttribute();
        if (attr != null) {
          for (PsiElement child : macrodef.getChildren()) {
            if (child instanceof AntStructuredElement && attr.getName().equals(((AntStructuredElement)child).getName())) {
              return myResolvedElement = child;
            }
          }
        }
        return myResolvedElement = macrodef;
      }
      return myResolvedElement = findClass(elementDef, element);
    }
    return null;
  }

  private static PsiElement findClass(final AntTypeDefinition elementDef, final AntStructuredElement element) {
    return element;
    /*final String clazz = elementDef.getClassName();
    return element.getManager().findClass(clazz, GlobalSearchScope.allScope(element.getProject()));*/
  }
}
