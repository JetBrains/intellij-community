package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.psi.AntAllTasksContainer;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntMacroDef;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringSetSpinAllocator;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class AntMacroDefParameterReference extends AntGenericReference {

  private final XmlElement myXmlElement;

  public AntMacroDefParameterReference(final GenericReferenceProvider provider,
                                       final AntElement antElement,
                                       final String str,
                                       final TextRange textRange,
                                       final XmlElement xmlElement) {
    super(provider, antElement, str, textRange, null);
    myXmlElement = xmlElement;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntElement element = getElement();
    final String oldName = getCanonicalText();
    if (!oldName.equals(newElementName)) {
      if (myXmlElement instanceof XmlAttributeValue) {
        final String text = myXmlElement.getText();
        if (text.length() > 2) {
          ((XmlAttribute)myXmlElement.getParent())
            .setValue(text.substring(1, text.length() - 1).replace("@{" + oldName + '}', "@{" + newElementName + '}'));
        }
      }
      else {
        final XmlTagValue tagValue = ((XmlTag)myXmlElement).getValue();
        tagValue.setText(tagValue.getText().replace("@{" + oldName + '}', "@{" + newElementName + '}'));
      }
      //element.subtreeChanged();
    }
    return element;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    return handleElementRename(((PsiNamedElement)element).getName());
  }

  public PsiElement resolve() {
    AntMacroDef macrodef = PsiTreeUtil.getParentOfType(getElement(), AntMacroDef.class);
    if (macrodef != null) {
      final String name = getCanonicalText();
      for (PsiElement child : macrodef.getChildren()) {
        if (child instanceof AntStructuredElement && !(child instanceof AntAllTasksContainer)) {
          if (name.equals(((AntStructuredElement)child).getName())) {
            return child;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  public IntentionAction[] getFixes() {
    return super.getFixes();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public Object[] getVariants() {
    Set<String> variants = StringSetSpinAllocator.alloc();
    try {
      AntMacroDef macrodef = PsiTreeUtil.getParentOfType(getElement(), AntMacroDef.class);
      if (macrodef != null) {
        for (PsiElement child : macrodef.getChildren()) {
          if (child instanceof AntStructuredElement) {
            AntStructuredElement element = (AntStructuredElement)child;
            if (element.getSourceElement().getName().equals("attribute")) {
              variants.add(element.getName());
            }
          }
        }
      }
      final int count = variants.size();
      return (count == 0) ? ourEmptyIntentions : variants.toArray(new String[count]);
    }
    finally {
      StringSetSpinAllocator.dispose(variants);
    }
  }
}
