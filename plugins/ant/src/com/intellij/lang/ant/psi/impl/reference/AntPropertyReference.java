package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.misc.PsiElementSetSpinAllocator;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.quickfix.AntCreatePropertyFix;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.StringSetSpinAllocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    return handleElementRename(((PsiNamedElement)element).getName());
  }

  public ReferenceType getType() {
    return getReferenceType();
  }

  public ReferenceType getSoftenType() {
    return getReferenceType();
  }

  public PsiElement resolve() {
    final AntFile antFile = getElement().getAntFile();
    final String text = getCanonicalText();
    final AntProperty resolved = antFile != null? antFile.getProperty(text) : null;
    if (resolved != null) {
      final PropertiesFile propFile = resolved.getPropertiesFile();
      if (propFile != null) {
        final String prefix = resolved.getPrefix();
        final String propertyName;
        if (prefix != null && text.startsWith(prefix) && prefix.length() < text.length() && text.charAt(prefix.length()) == '.') {
          propertyName = text.substring(prefix.length() + 1);
        }
        else {
          propertyName = text;
        }
        return propFile.findPropertyByKey(propertyName);
      }
      return resolved.getFormatElement();
    }
    return null;
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("unknown.property", getCanonicalText());
  }

  public Object[] getVariants() {
    final AntElement currentElement = getElement();
    final Set<PsiElement> variants = PsiElementSetSpinAllocator.alloc();
    final Set<String> definedProperties = StringSetSpinAllocator.alloc();
    try {
      final Set<PsiElement> elementsDepthStack = PsiElementSetSpinAllocator.alloc();
      try {
        final AntProject project = currentElement.getAntProject();
        final AntFile antFile = currentElement.getAntFile();
        for (final AntProperty property : antFile.getProperties()) {
          if (currentElement != property) {
            addAntProperties(property, variants, definedProperties);
          }
        }
        getVariants(project, variants, elementsDepthStack, definedProperties);
        for (final AntFile imported : project.getImportedFiles()) {
          getVariants(imported.getAntProject(), variants, elementsDepthStack, definedProperties);
        }
      }
      finally {
        PsiElementSetSpinAllocator.dispose(elementsDepthStack);
      }
      return variants.toArray(new Object[variants.size()]);
    }
    finally {
      PsiElementSetSpinAllocator.dispose(variants);
      StringSetSpinAllocator.dispose(definedProperties);
    }
  }

  @NotNull
  public IntentionAction[] getFixes() {
    final String name = getCanonicalRepresentationText();
    if (name == null || name.length() == 0) return EMPTY_INTENTIONS;

    final List<IntentionAction> result = new ArrayList<IntentionAction>();
    final AntProject project = getElement().getAntProject();
    result.add(new AntCreatePropertyFix(this));
    final Set<String> files = StringSetSpinAllocator.alloc();
    try {
      for (final PsiElement child : project.getChildren()) {
        if (child instanceof AntProperty) {
          final PropertiesFile propFile = ((AntProperty)child).getPropertiesFile();
          if (propFile != null) {
            final String fileName = propFile.getName();
            if (!files.contains(fileName)) {
              files.add(fileName);
              result.add(new AntCreatePropertyFix(this, propFile));
            }
          }
        }
      }
    }
    finally {
      StringSetSpinAllocator.dispose(files);
    }
    return result.toArray(new IntentionAction[result.size()]);
  }

  private void getVariants(final AntStructuredElement element, final Set<PsiElement> variants, final Set<PsiElement> elementsDepthStack, final Set<String> definedProperties) {
    if (elementsDepthStack.contains(element)) return;
    elementsDepthStack.add(element);
    final AntElement currentElement = getElement();
    try {
      for (final PsiElement child : element.getChildren()) {
        if (child instanceof AntStructuredElement) {
          getVariants((AntStructuredElement)child, variants, elementsDepthStack, definedProperties);
          if (child instanceof AntImport) {
            AntImport antImport = (AntImport)child;
            final AntFile imported = antImport.getImportedFile();
            if (imported != null) {
              getVariants(imported.getAntProject(), variants, elementsDepthStack, definedProperties);
            }
          }
          else if (child instanceof AntProperty) {
            AntProperty property = (AntProperty)child;
            final PropertiesFile propertiesFile = property.getPropertiesFile();
            if (propertiesFile == null) {
              if (currentElement != property) {
                addAntProperties(property, variants, definedProperties);
              }
            }
            else {
              final String prefix = property.getPrefix();
              for (final Property importedProp : propertiesFile.getProperties()) {
                final String propName = prefix != null? prefix + "." + importedProp.getName() : importedProp.getName();
                if (!definedProperties.contains(propName)) {
                  variants.add(importedProp);
                  definedProperties.add(propName);
                }
              }
            }
          }
        }
      }
    }
    finally {
      elementsDepthStack.remove(element);
    }
  }

  private static void addAntProperties(final AntProperty property, final Set<PsiElement> variants, final Set<String> definedProperties) {
    final String[] names = property.getNames();
    if (names != null) {
      final Project project = property.getProject();
      for (final String name : names) {
        if (!definedProperties.contains(name)) {
          variants.add(new AntElementCompletionWrapper(name, project, AntElementRole.PROPERTY_ROLE));
          definedProperties.add(name);
        }
      }
    }
  }

  private static ReferenceType getReferenceType() {
    return ourRefType;
  }
}
