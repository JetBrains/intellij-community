package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.impl.AntAntImpl;
import com.intellij.lang.ant.quickfix.AntCreateTargetAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntTargetReference extends AntGenericReference {

  private static final ReferenceType ourRefType = new ReferenceType(ReferenceType.ANT_TARGET);
  private boolean myShouldBeSkippedByAnnotator;

  public AntTargetReference(final GenericReferenceProvider provider,
                            final AntElement antElement,
                            final String str,
                            final TextRange textRange,
                            final XmlAttribute attribute) {
    super(provider, antElement, str, textRange, attribute);
    setShouldBeSkippedByAnnotator(false);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntElement element = getElement();
    if (element instanceof AntProject || element instanceof AntCall) {
      getAttribute().setValue(newElementName);
    }
    else if (element instanceof AntTarget) {
      int start = getElementStartOffset() + getReferenceStartOffset() - getAttributeValueStartOffset();
      final String value = getAttribute().getValue();
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        if (start > 0) {
          builder.append(value.substring(0, start));
        }
        builder.append(newElementName);
        if (value.length() > start + getRangeInElement().getLength()) {
          builder.append(value.substring(start + getRangeInElement().getLength()));
        }
        getAttribute().setValue(builder.toString());
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    return element;
  }

  @Nullable
  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof AntTarget) {
      final PsiNamedElement psiNamedElement = (PsiNamedElement)element;
      return handleElementRename(psiNamedElement.getName());
    }
    throw new IncorrectOperationException("Can bind only to ant targets.");
  }


  public PsiElement resolve() {
    final String name = getCanonicalRepresentationText();
    final AntElement element = getElement();
    final AntProject project = element.getAntProject();
    AntTarget result = project.getTarget(name);
    if (result == null) {
      for (final AntTarget target : project.getImportTargets()) {
        if (name.equals(target.getName())) {
          return target;
        }
      }
    }
    if (result == null && element instanceof AntAntImpl) {
      AntAntImpl ant = (AntAntImpl)element;
      final PsiFile psiFile = ant.findFileByName(ant.getFileName());
      if (psiFile != null) {
        AntFile antFile;
        if (psiFile instanceof AntFile) {
          antFile = (AntFile)psiFile;
        }
        else {
          antFile = (AntFile)psiFile.getViewProvider().getPsi(AntSupport.getLanguage());
        }
        if (antFile != null) {
          final AntProject antProject = antFile.getAntProject();
          if (antProject != null) {
            result = antProject.getTarget(name);
          }
        }
      }
    }
    return result;
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

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("cannot.resolve.target", getCanonicalRepresentationText());
  }

  public boolean shouldBeSkippedByAnnotator() {
    return myShouldBeSkippedByAnnotator;
  }

  public void setShouldBeSkippedByAnnotator(boolean value) {
    myShouldBeSkippedByAnnotator = value;
  }

  @NotNull
  public IntentionAction[] getFixes() {
    final AntProject project = getElement().getAntProject();
    final AntFile[] importedFiles = project.getImportedFiles();
    IntentionAction[] result = new IntentionAction[importedFiles.length + 1];
    result[0] = new AntCreateTargetAction(this);
    for (int i = 0; i < importedFiles.length; ++i) {
      result[i + 1] = new AntCreateTargetAction(this, importedFiles[i]);
    }
    return result;
  }

  private int getElementStartOffset() {
    return getElement().getTextRange().getStartOffset();
  }

  private int getReferenceStartOffset() {
    return getRangeInElement().getStartOffset();
  }

  private int getAttributeValueStartOffset() {
    final XmlAttribute attr = getAttribute();
    final XmlAttributeValue valueElement = attr.getValueElement();
    return (valueElement == null) ? attr.getTextRange().getEndOffset() + 1 : valueElement.getTextRange().getStartOffset() + 1;
  }
}