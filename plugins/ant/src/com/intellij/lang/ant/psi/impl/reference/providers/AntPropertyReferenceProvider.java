package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.impl.AntElementImpl;
import com.intellij.lang.ant.psi.impl.reference.AntPropertyReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AntPropertyReferenceProvider extends GenericReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    AntStructuredElement antElement = (AntStructuredElement)element;
    final XmlTag sourceElement = antElement.getSourceElement();
    final XmlAttribute[] attributes = sourceElement.getAttributes();
    if (attributes.length > 0) {
      final List<PsiReference> refs = PsiReferenceListSpinAllocator.alloc();
      try {
        boolean isTarget = antElement instanceof AntTarget;
        boolean isSet = "isset".equals(sourceElement.getName());
        for (XmlAttribute attr : attributes) {
          @NonNls final String attName = attr.getName();
          if (isTarget && ("if".equals(attName) || "unless".equals(attName))) {
            getAttributeReference(antElement, attr, refs);
          }
          else if (isSet && "property".equals(attName)) {
            getAttributeReference(antElement, attr, refs);
          }
          else {
            getAttributeReferences(antElement, attr, refs);
          }
        }
        if (refs.size() > 0) {
          return refs.toArray(new PsiReference[refs.size()]);
        }
      }
      finally {
        PsiReferenceListSpinAllocator.dispose(refs);
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  /**
   * Gets all references to the ${} properties.
   *
   * @param element
   * @param attr
   * @param refs
   */
  private void getAttributeReferences(final AntElement element, final XmlAttribute attr, final List<PsiReference> refs) {
    final AntProject project = element.getAntProject();
    final String value = attr.getValue();
    final XmlAttributeValue xmlAttributeValue = attr.getValueElement();
    if (xmlAttributeValue != null) {
      final int offsetInPosition = xmlAttributeValue.getTextRange().getStartOffset() - element.getTextRange().getStartOffset() + 1;
      int startIndex;
      int endIndex = -1;
      while ((startIndex = value.indexOf("${", endIndex + 1)) > endIndex) {
        startIndex += 2;
        endIndex = value.indexOf('}', startIndex);
        if (endIndex < 0) break;
        if (endIndex > startIndex) {
          final String propName = value.substring(startIndex, endIndex);
          if (project.isEnvironmentProperty(propName) && AntElementImpl.resolveProperty(element, propName) == null) {
            continue;
          }
          refs.add(new AntPropertyReference(this, element, propName,
                                            new TextRange(offsetInPosition + startIndex, offsetInPosition + endIndex), attr));
        }
      }
    }
  }

  /**
   * Gets single reference on a property named as the attribute's and if the property is resolved.
   *
   * @param element
   * @param attr
   * @param refs
   */
  private void getAttributeReference(final AntElement element, final XmlAttribute attr, final List<PsiReference> refs) {
    final AntProject project = element.getAntProject();
    final String value = attr.getValue();
    final PsiElement resolvedProp = AntElementImpl.resolveProperty(element, value);
    if (project.isEnvironmentProperty(value) && resolvedProp == null) {
      return;
    }
    final XmlAttributeValue xmlAttributeValue = attr.getValueElement();
    if (xmlAttributeValue != null && resolvedProp != null) {
      final int offsetInPosition = xmlAttributeValue.getTextRange().getStartOffset() - element.getTextRange().getStartOffset() + 1;
      refs.add(new AntPropertyReference(this, element, value, new TextRange(offsetInPosition, offsetInPosition + value.length()), attr));
    }
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }
}
