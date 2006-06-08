package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.misc.AntPsiUtil;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.resources.AntBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class AntRefIdReference extends AntGenericReference {

  public AntRefIdReference(final GenericReferenceProvider provider,
                           final AntElement antElement,
                           final String str,
                           final TextRange textRange,
                           final XmlAttribute attribute) {
    super(provider, antElement, str, textRange, attribute);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntElement element = getElement();
    if (element instanceof AntStructuredElement) {
      getAttribute().setValue(newElementName);
    }
    return element;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof AntStructuredElement) {
      final PsiNamedElement psiNamedElement = (PsiNamedElement)element;
      return handleElementRename(psiNamedElement.getName());
    }
    throw new IncorrectOperationException("Can bind only to ant structured elements.");
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.getMessage("cannot.resolve.refid", getCanonicalText());
  }

  public PsiElement resolve() {
    final AntStructuredElement element = (AntStructuredElement)getElement();
    final String id = getCanonicalText();
    AntElement refId = element.getElementByRefId(id);
    if (refId == null) {
      final AntElement anchor = AntPsiUtil.getSubProjectElement(element);
      for (final AntFile file : AntPsiUtil.getImportedFiles(element.getAntProject(), anchor)) {
        final AntProject importedProject = file.getAntProject();
        importedProject.getChildren();
        refId = importedProject.getElementByRefId(id);
        if (refId != null) {
          return refId;
        }
      }
    }
    return refId;
  }

  public Object[] getVariants() {
    return getVariants(getElement().getAntProject());
  }

  @NotNull
  public IntentionAction[] getFixes() {
    return super.getFixes();
  }

  private static String[] getVariants(AntStructuredElement element) {
    final Set<String> variants = new HashSet<String>();
    appendSet(variants, element.getRefIds());
    for (PsiElement child : element.getChildren()) {
      if (child instanceof AntStructuredElement) {
        appendSet(variants, getVariants((AntStructuredElement)child));
      }
    }
    return variants.toArray(new String[variants.size()]);
  }

  private static void appendSet(final Set<String> set, final String[] strs) {
    for (String str : strs) {
      set.add(str);
    }
  }
}
