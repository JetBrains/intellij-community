package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.psi.AntMacroDef;
import com.intellij.lang.ant.psi.AntPresetDef;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringSetSpinAllocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class AntElementNameReference extends AntGenericReference {

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
      final AntStructuredElement defElement = (AntStructuredElement)typeDef.getDefiningElement();
      if (defElement != null && (defElement instanceof AntPresetDef || (defElement.getParent()instanceof AntMacroDef &&
                                                                        "element".equals(defElement.getSourceElement().getName())))) {
        // renaming macrodef's nested element
        element.getSourceElement().setName(newElementName);
      }
    }
    else {
      AntTask task = (AntTask)element;
      if (task.isMacroDefined() || task.isPresetDefined() || task.isTypeDefined()) {
        final XmlAttribute attr = getAttribute();
        if (attr == null) {
          // renaming macrodef, presetdef or typedef/taskdef itself
          task.getSourceElement().setName(newElementName);
        }
        else {
          attr.setName(newElementName);
        }
      }
    }

    return element;
  }

  @Nullable
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
      final PsiElement definingElement = elementDef.getDefiningElement();
      if (element.isPresetDefined() || element.isTypeDefined()) {
        return definingElement;
      }
      if (!(element instanceof AntTask)) {
        return (definingElement == null) ? findClass(elementDef, element) : definingElement;
      }
      AntTask task = (AntTask)element;
      if (task.isMacroDefined()) {
        final XmlAttribute attr = getAttribute();
        if (definingElement != null && attr != null) {
          for (PsiElement child : definingElement.getChildren()) {
            if (child instanceof AntStructuredElement && attr.getName().equals(((AntStructuredElement)child).getName())) {
              return child;
            }
          }
        }
        return definingElement;
      }
      return findClass(elementDef, element);
    }
    return null;
  }


  public boolean shouldBeSkippedByAnnotator() {
    return true;
  }

  public Object[] getVariants() {
    AntStructuredElement parent = (AntStructuredElement)getElement().getAntParent();
    if( parent == null) {
      return EMPTY_ARRAY;
    }
    AntTypeDefinition def = parent.getTypeDefinition();
    if (def == null) {
      def = parent.getAntProject().getTypeDefinition();
      if (def == null) {
        return EMPTY_ARRAY;
      }
    }
    final Set<String> ids = StringSetSpinAllocator.alloc();
    try {
      for (AntTypeId id : def.getNestedElements()) {
        ids.add(id.getName());
      }
      return ids.toArray(new Object[ids.size()]);
    }
    finally {
      StringSetSpinAllocator.dispose(ids);
    }
  }

  @NotNull
  public IntentionAction[] getFixes() {
    return super.getFixes();
  }

  @Nullable
  private static PsiElement findClass(final AntTypeDefinition elementDef, final AntStructuredElement element) {
    final String clazz = elementDef.getClassName();
    return element.getManager().findClass(clazz, GlobalSearchScope.allScope(element.getProject()));
  }
}
