package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.misc.PsiElementSetSpinAllocator;
import com.intellij.lang.ant.psi.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringSetSpinAllocator;
import org.jetbrains.annotations.NotNull;
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
    final AntStructuredElement element = getElement();
    final String id = getCanonicalRepresentationText();
    AntElement refId = element.getElementByRefId(id);
    if (refId == null) {
      for (final AntFile file : element.getAntProject().getImportedFiles()) {
        final AntProject importedProject = file.getAntProject();
        importedProject.getChildren();
        refId = importedProject.getElementByRefId(id);
        if (refId != null) break;
      }
    }
    if (refId == null) {
      final AntTarget target = PsiTreeUtil.getParentOfType(element, AntTarget.class);
      if (target != null) {
        final Set<PsiElement> targetStack = PsiElementSetSpinAllocator.alloc();
        try {
          refId = resolveTargetRefId(target, id, targetStack);
        }
        finally {
          PsiElementSetSpinAllocator.dispose(targetStack);
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
      for (final AntFile imported : project.getImportedFiles()) {
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

  @Nullable
  private static AntElement resolveTargetRefId(final AntTarget target, final String id, final Set<PsiElement> stack) {
    AntElement result = null;
    if (!stack.contains(target)) {
      result = target.getElementByRefId(id);
      if (result == null) {
        stack.add(target);
        for (final AntTarget dependie : target.getDependsTargets()) {
          if ((result = resolveTargetRefId(dependie, id, stack)) != null) break;
        }
      }
    }
    return result;
  }

  private static void getVariants(final AntStructuredElement element, final Set<String> variants) {
    for (final String str : element.getRefIds()) {
      variants.add(str);
    }
    for (final PsiElement child : element.getChildren()) {
      if (child instanceof AntStructuredElement) {
        getVariants((AntStructuredElement)child, variants);
      }
    }
  }
}
