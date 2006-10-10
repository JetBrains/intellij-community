package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.misc.PsiElementSetSpinAllocator;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

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


  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("cannot.resolve.refid", getCanonicalRepresentationText());
  }

  public PsiElement resolve() {
    final String id = getCanonicalRepresentationText();
    final Set<PsiElement> elementsDepthStack = PsiElementSetSpinAllocator.alloc();
    try {
      return resolve(id, getElement().getAntProject(), elementsDepthStack);
    }
    finally {
      PsiElementSetSpinAllocator.dispose(elementsDepthStack);
    }
  }

  public Object[] getVariants() {
    final Set<PsiElement> variants = PsiElementSetSpinAllocator.alloc();
    try {
      final Set<PsiElement> elementsDepthStack = PsiElementSetSpinAllocator.alloc();
      try {
        getVariants(getElement().getAntProject(), variants, elementsDepthStack);
        return variants.toArray(new Object[variants.size()]);
      }
      finally {
        PsiElementSetSpinAllocator.dispose(elementsDepthStack);
      }
    }
    finally {
      PsiElementSetSpinAllocator.dispose(variants);
    }
  }

  @Nullable
  private static AntElement resolve(final String id, final AntProject project, final Set<PsiElement> elementsDepthStack) {
    if (elementsDepthStack.contains(project)) return null;
    elementsDepthStack.add(project);
    try {
      AntElement refId = project.getElementByRefId(id);
      if (refId == null) {
        for (final AntFile file : project.getImportedFiles()) {
          refId = resolve(id, file.getAntProject(), elementsDepthStack);
          if (refId != null) break;
        }
      }
      return refId;
    }
    finally {
      elementsDepthStack.remove(project);
    }
  }

  private static void getVariants(final AntProject project, final Set<PsiElement> variants, final Set<PsiElement> elementsDepthStack) {
    if (elementsDepthStack.contains(project)) return;
    elementsDepthStack.add(project);
    try {
      for (final String id : project.getRefIds()) {
        final AntElement refElement = project.getElementByRefId(id);
        if (refElement != null) {
          variants.add(refElement);
        }
      }
      for (final AntFile file : project.getImportedFiles()) {
        getVariants(file.getAntProject(), variants, elementsDepthStack);
      }
    }
    finally {
      elementsDepthStack.remove(project);
    }
  }
}
