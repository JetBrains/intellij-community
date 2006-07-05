package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.misc.StringSetSpinAllocator;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.impl.AntElementImpl;
import com.intellij.lang.ant.quickfix.AntCreatePropertyAction;
import com.intellij.lang.ant.resources.AntBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
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
    final Set<String> variants = StringSetSpinAllocator.alloc();
    try {
      final AntProject project = getElement().getAntProject();
      for (final AntProperty property : project.getProperties()) {
        variants.add(property.getName());
      }
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
    List<IntentionAction> result = new ArrayList<IntentionAction>();
    final AntProject project = getElement().getAntProject();
    result.add(new AntCreatePropertyAction(this));
    for (final PsiElement child : project.getChildren()) {
      if (child instanceof AntProperty) {
        PropertiesFile propFile = ((AntProperty)child).getPropertiesFile();
        if (propFile != null) {
          result.add(new AntCreatePropertyAction(this, propFile));
        }
      }
    }
    return result.toArray(new IntentionAction[result.size()]);
  }

  private static void getVariants(final AntStructuredElement element, final Set<String> variants) {
    for (final PsiElement child : element.getChildren()) {
      if (child instanceof AntStructuredElement) {
        getVariants((AntStructuredElement)child, variants);
        if (child instanceof AntProperty) {
          AntProperty property = (AntProperty)child;
          final PropertiesFile propertiesFile = property.getPropertiesFile();
          if (propertiesFile == null) {
            final String name = property.getName();
            if (name != null) {
              variants.add(name);
            }
          }
          else {
            for (final Property importedProp : propertiesFile.getProperties()) {
              final String name = importedProp.getKey();
              if (name != null) {
                variants.add(name);
              }
            }
          }
        }
      }
    }
  }
}
