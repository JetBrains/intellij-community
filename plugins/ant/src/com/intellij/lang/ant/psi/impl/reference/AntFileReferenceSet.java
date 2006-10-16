package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSetBase;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class AntFileReferenceSet extends FileReferenceSetBase {

  public AntFileReferenceSet(final AntStructuredElement element,
                             final XmlAttributeValue value,
                             final GenericReferenceProvider provider) {
    super(StringUtil.stripQuotesAroundValue(value.getText()), element,
          value.getTextRange().getStartOffset() - element.getTextRange().getStartOffset() + 1, provider, true);
  }

  protected AntFileReference createFileReference(final TextRange range, final int index, final String text) {
    return new AntFileReference(this, range, index, text);
  }

  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  @Nullable
  public String getPathString() {
    return getElement().computeAttributeValue(super.getPathString());
  }

  public boolean isAbsolutePathReference() {
    return super.isAbsolutePathReference() || new File(getPathString()).isAbsolute();
  }

  /*@NotNull
  public Collection<PsiElement> getDefaultContexts(PsiElement element) {
    return Collections.emptyList();
  }*/
}
