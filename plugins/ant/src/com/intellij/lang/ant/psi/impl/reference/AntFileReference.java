package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntImport;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AntFileReference extends AntGenericReference {

  private static final ReferenceType ourRefType = new ReferenceType(ReferenceType.FILE);

  public AntFileReference(final GenericReferenceProvider provider,
                          final AntStructuredElement antElement,
                          final String str,
                          final TextRange textRange) {
    super(provider, antElement, str, textRange, null);
  }

  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntStructuredElement antElement = getElement();
    final XmlTag sourceElement = antElement.getSourceElement();
    if (sourceElement.getAttributeValue("file") != null) {
      sourceElement.setAttribute("file", newElementName);
    }
    return antElement;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiFile) {
      final PsiFile psiFile = (PsiFile)element;
      return handleElementRename(psiFile.getName());
    }
    throw new IncorrectOperationException("Can bind only to properties files.");
  }

  public static ReferenceType getReferenceType() {
    return ourRefType;
  }

  public ReferenceType getType() {
    return getReferenceType();
  }

  public ReferenceType getSoftenType() {
    return getReferenceType();
  }

  public PsiElement resolve() {
    final AntStructuredElement se = getElement();
    return se.findFileByName(getCanonicalText(), se instanceof AntImport);
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("file.doesnt.exist", getCanonicalRepresentationText());
  }

  @NotNull
  public IntentionAction[] getFixes() {
    return super.getFixes();
  }
}