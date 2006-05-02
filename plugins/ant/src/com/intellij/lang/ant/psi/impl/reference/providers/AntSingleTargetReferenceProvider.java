package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.impl.AntCallImpl;
import com.intellij.lang.ant.psi.impl.AntProjectImpl;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class AntSingleTargetReferenceProvider extends AntTargetReferenceProviderBase {

  @NonNls private static final Map<Class,String> ourTypesToAttributeNames;

  static {
    ourTypesToAttributeNames = new HashMap<Class, String>();
    ourTypesToAttributeNames.put(AntProjectImpl.class, "default");
    ourTypesToAttributeNames.put(AntCallImpl.class, "target");
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    String attributeName = ourTypesToAttributeNames.get(element.getClass());
    if( attributeName == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    AntElement antElement = (AntElement)element;
    final XmlAttribute attr = ((XmlTag)antElement.getSourceElement()).getAttribute(attributeName, null);
    if (attr == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final XmlAttributeValue valueElement = attr.getValueElement();
    if( valueElement == null ) {
      return PsiReference.EMPTY_ARRAY;
    }
    final int offsetInPosition = valueElement.getTextRange().getStartOffset() - antElement.getTextRange().getStartOffset() + 1;
    final String attrValue = valueElement.getValue();
    return new PsiReference[]{
      new AntTargetReference(this, antElement, attrValue, new TextRange(offsetInPosition, offsetInPosition + attrValue.length()), attr)};
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
