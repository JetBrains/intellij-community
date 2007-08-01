package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.misc.PsiElementSetSpinAllocator;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.quickfix.AntCreatePropertyFix;
import com.intellij.lang.properties.psi.PropertiesFile;
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
    final XmlAttribute attr = getAttribute();
    final XmlAttributeValue attrValue = attr.getValueElement();
    if (attrValue != null) {
      final int valueStartOffset = attrValue.getTextRange().getStartOffset() - element.getTextRange().getStartOffset() + 1;
      final String value = attr.getValue();
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        final AntFile antFile = element.getAntFile();
        final AntProperty resolved = antFile != null? antFile.getProperty(oldName) : null;
        final String prefix = resolved != null? resolved.getPrefix() : null;
        final int startOffset = getRangeInElement().getStartOffset();
        final int endOffset = getRangeInElement().getEndOffset();
        if (valueStartOffset < startOffset) {
          builder.append(value.substring(0, startOffset - valueStartOffset));
        }
        if (prefix != null) {
          builder.append(prefix).append(".");
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
    return element;
  }

  @Nullable
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
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
      final String propName = cutPrefix(text, resolved.getPrefix());
      final PropertiesFile propFile = resolved.getPropertiesFile();
      if (propFile != null) {
        return propFile.findPropertyByKey(propName);
      }
      return resolved.getFormatElement(propName);
    }
    return null;
  }

  private static String cutPrefix(final String text, final String prefix) {
    if (prefix != null && text.startsWith(prefix) && prefix.length() < text.length() && text.charAt(prefix.length()) == '.') {
      return text.substring(prefix.length() + 1);
    }
    return text;
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("unknown.property", getCanonicalText());
  }

  public Object[] getVariants() {
    final AntElement currentElement = getElement();
    final Set<PsiElement> variants = PsiElementSetSpinAllocator.alloc();
    try {
      final AntFile antFile = currentElement.getAntFile();
      final Project project = currentElement.getProject();
      for (final AntProperty property : antFile.getProperties()) {
        if (currentElement == property) {
          continue;
        }
        final String[] names = property.getNames();
        if (names != null) {
          for (final String name : names) {
            variants.add(new AntElementCompletionWrapper(name, project, AntElementRole.PROPERTY_ROLE));
          }
        }
      }
      return variants.toArray(new Object[variants.size()]);
    }
    finally {
      PsiElementSetSpinAllocator.dispose(variants);
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

  private static ReferenceType getReferenceType() {
    return ourRefType;
  }
}
