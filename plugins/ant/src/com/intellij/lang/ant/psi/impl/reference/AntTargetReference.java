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

import java.util.ArrayList;
import java.util.List;

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
    if (element instanceof AntProject || element instanceof AntCall || element instanceof AntAnt) {
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
    if (name == null) return null;

    final AntElement element = getElement();
    AntTarget result = null;

    if (element instanceof AntAntImpl) {
      final PsiFile psiFile = ((AntAntImpl)element).getCalledAntFile();
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
    if (result == null) {
      final AntProject project = element.getAntProject();
      result = project.getTarget(name);
      if (result == null) {
        for (final AntTarget target : project.getImportedTargets()) {
          if (name.equals(target.getName())) {
            result = target;
            break;
          }
        }
        if (result == null) {
          for (final AntTarget target : project.getImportedTargets()) {
            if (name.equals(target.getQualifiedName())) {
              result = target;
              break;
            }
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

  public Object[] getVariants() {
    final AntElement element = getElement();
    if (element instanceof AntAntImpl) {
      final PsiFile psiFile = ((AntAntImpl)element).getCalledAntFile();
      if (psiFile != null) {
        AntFile antFile;
        if (psiFile instanceof AntFile) {
          antFile = (AntFile)psiFile;
        }
        else {
          antFile = (AntFile)psiFile.getViewProvider().getPsi(AntSupport.getLanguage());
        }
        final AntProject project = (antFile == null) ? null : antFile.getAntProject();
        if (project != null) {
          return project.getTargets();
        }
      }
    }
    return super.getVariants();
  }

  @NotNull
  public IntentionAction[] getFixes() {
    final String name = getCanonicalRepresentationText();
    if (name == null || name.length() == 0) return ourEmptyIntentions;

    final AntProject project = getElement().getAntProject();
    final AntFile[] importedFiles = project.getImportedFiles();
    final List<IntentionAction> result = new ArrayList<IntentionAction>(importedFiles.length + 1);
    result.add(new AntCreateTargetAction(this));
    for (final AntFile file : importedFiles) {
      if (file.isPhysical()) {
        result.add(new AntCreateTargetAction(this, file));
      }
    }
    return result.toArray(new IntentionAction[result.size()]);
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