package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;

public class AntElementNameReference extends AntGenericReference {

  public AntElementNameReference(final GenericReferenceProvider provider,
                                 final AntStructuredElement element) {
    super(provider, element);
  }

  public AntElementNameReference(final GenericReferenceProvider provider,
                                 final AntStructuredElement element,
                                 final XmlAttribute attr) {
    super(provider, element, attr);
  }

  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntStructuredElement element = getElement();
    if (element instanceof AntTask) {
      AntTask task = (AntTask)element;
      if (task.isMacroDefined()) {
        final XmlAttribute attr = getAttribute();
        if (attr == null) {
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
    final AntStructuredElement element = getElement();
    final AntTypeDefinition elementDef = element.getTypeDefinition();
    if (elementDef != null) {
      if (!(element instanceof AntTask)) {
        return findClass(elementDef, element);
      }
      AntTask task = (AntTask)element;
      if (task.isMacroDefined()) {
        final PsiElement macrodef = elementDef.getDefiningElement();
        final XmlAttribute attr = getAttribute();
        if (attr != null) {
          for (PsiElement child : macrodef.getChildren()) {
            if (child instanceof AntStructuredElement &&
                attr.getName().equals(((AntStructuredElement)child).getName())) {
              return child;
            }
          }
        }
        return macrodef;
      }
      return findClass(elementDef, element);
    }
    return null;
  }

  private static PsiElement findClass(final AntTypeDefinition elementDef,
                                      final AntStructuredElement element) {
    return element;
    /*final String clazz = elementDef.getClassName();
    return element.getManager().findClass(clazz, GlobalSearchScope.allScope(element.getProject()));*/
  }
}
