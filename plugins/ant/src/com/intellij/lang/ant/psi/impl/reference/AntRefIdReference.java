package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.misc.AntPsiUtil;
import com.intellij.lang.ant.misc.StringSetSpinAllocator;
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
    final Set<String> variants = StringSetSpinAllocator.alloc();
    try {
      final AntProject project = getElement().getAntProject();
      getVariants(project, variants);
      for (AntFile imported : AntPsiUtil.getImportedFiles(project)) {
        getVariants(imported.getAntProject(), variants);
      }
      return variants.toArray(new String[variants.size()]);
    }
    finally {
      StringSetSpinAllocator.dispose(variants);
    }
  }

  @NotNull
  public IntentionAction[] getFixes() {
    return super.getFixes();
  }

  private static void getVariants(final AntStructuredElement element, final Set<String> variants) {
    for (String str : element.getRefIds()) {
      variants.add(str);
    }
    for (PsiElement child : element.getChildren()) {
      if (child instanceof AntStructuredElement) {
        getVariants((AntStructuredElement)child, variants);
      }
    }
  }
}
