package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.AntElementImpl;
import com.intellij.lang.ant.quickfix.AntCreatePropertyAction;
import com.intellij.lang.ant.resources.AntBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AntPropertyReference extends AntGenericReference {

  private static final ReferenceType ourRefType = new ReferenceType(ReferenceType.ANT_PROPERTY);

  public AntPropertyReference(final GenericReferenceProvider provider,
                              final AntElement antElement,
                              final String str,
                              final TextRange textRange,
                              final XmlAttribute attribute) {
    super(provider, antElement, str, textRange, attribute);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntElement element = getElement();
    final String oldName = getCanonicalText();
    if (!oldName.equals(newElementName)) {
      final XmlAttribute attr = getAttribute();
      final XmlAttributeValue attrValue = attr.getValueElement();
      if (attrValue != null) {
        final int valueStartOffset = attrValue.getTextRange().getStartOffset() - element.getTextRange().getStartOffset() + 1;
        final String value = attr.getValue();
        final StringBuilder builder = StringBuilderSpinAllocator.alloc();
        try {
          final int startOffset = getRangeInElement().getStartOffset();
          final int endOffset = getRangeInElement().getEndOffset();
          if (valueStartOffset < startOffset) {
            builder.append(value.substring(0, startOffset - valueStartOffset));
          }
          builder.append(newElementName);
          if (endOffset < valueStartOffset + value.length()) {
            builder.append(value.substring(endOffset - valueStartOffset));
          }
          attr.setValue(builder.toString());
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }
      }
    }
    return element;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    return handleElementRename(((PsiNamedElement)element).getName());
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
    return AntElementImpl.resolveProperty(getElement(), getCanonicalText());
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.getMessage("unknown.property", getCanonicalText());
  }

  public Object[] getVariants() {
    return getVariants(getElement().getAntProject());
  }

  @NotNull
  public IntentionAction[] getFixes() {
    List<IntentionAction> result = new ArrayList<IntentionAction>();
    final AntProject project = getElement().getAntProject();
    result.add(new AntCreatePropertyAction(this));
    for (PsiElement child : project.getChildren()) {
      if (child instanceof AntProperty) {
        PropertiesFile propFile = ((AntProperty)child).getPropertiesFile();
        if (propFile != null) {
          result.add(new AntCreatePropertyAction(this, propFile));
        }
      }
    }
    return result.toArray(new IntentionAction[result.size()]);
  }

  private static PsiElement[] getVariants(AntStructuredElement element) {
    final Set<PsiElement> variants = new HashSet<PsiElement>();
    appendSet(variants, element.getProperties());
    for (PsiElement child : element.getChildren()) {
      if (child instanceof AntStructuredElement) {
        appendSet(variants, getVariants((AntStructuredElement)child));
      }
    }
    return variants.toArray(new PsiElement[variants.size()]);
  }

  private static void appendSet(final Set<PsiElement> set, final PsiElement[] elements) {
    for (final PsiElement element : elements) {
      set.add(element);
    }
  }
}
