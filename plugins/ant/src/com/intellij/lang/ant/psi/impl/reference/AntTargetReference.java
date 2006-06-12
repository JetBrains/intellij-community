package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.misc.AntPsiUtil;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.quickfix.AntCreateTargetAction;
import com.intellij.lang.ant.resources.AntBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

public class AntTargetReference extends AntGenericReference {

  private static final ReferenceType ourRefType = new ReferenceType(ReferenceType.ANT_TARGET);

  public AntTargetReference(final GenericReferenceProvider provider,
                            final AntElement antElement,
                            final String str,
                            final TextRange textRange,
                            final XmlAttribute attribute) {
    super(provider, antElement, str, textRange, attribute);
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

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof AntTarget) {
      final PsiNamedElement psiNamedElement = (PsiNamedElement)element;
      return handleElementRename(psiNamedElement.getName());
    }
    throw new IncorrectOperationException("Can bind only to ant targets.");
  }


  public PsiElement resolve() {
    final String name = getCanonicalText();
    AntProject project = getElement().getAntProject();
    AntTarget result = project.getTarget(name);
    if (result == null) {
      final AntFile[] importedFiles = AntPsiUtil.getImportedFiles(project);
      for (AntFile imported : importedFiles) {
        if ((result = imported.getAntProject().getTarget(name)) != null) {
          break;
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
    return AntBundle.getMessage("cannot.resolve.target", getCanonicalText());
  }

  @NotNull
  public IntentionAction[] getFixes() {
    final AntProject project = getElement().getAntProject();
    final AntFile[] importedFiles = AntPsiUtil.getImportedFiles(project);
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