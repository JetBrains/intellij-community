package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.CachingReference;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AntGenericReference extends CachingReference implements AntReference {
  private final AntElement myAntElement;
  private final String myText;
  private final TextRange myTextRange;
  private final XmlAttribute myAttribute;

  protected AntGenericReference(final AntElement element, final String str, final TextRange textRange, final XmlAttribute attribute) {
    myAntElement = element;
    myText = str;
    myTextRange = textRange;
    myAttribute = attribute;
  }

  protected AntGenericReference(final AntStructuredElement element, final XmlAttribute attr) {
    myAntElement = element;
    myText = (attr == null) ? element.getSourceElement().getName() : attr.getName();
    int startInElement = (attr == null) ? 1 : attr.getTextRange().getStartOffset() - element.getTextRange().getStartOffset();
    myTextRange = new TextRange(startInElement, myText.length() + startInElement);
    myAttribute = attr;
  }

  protected AntGenericReference(final AntStructuredElement element) {
    this(element, null);
  }

  public AntElement getElement() {
    return myAntElement;
  }

  public TextRange getRangeInElement() {
    return myTextRange;
  }

  public String getCanonicalText() {
    return myText;
  }

  public boolean shouldBeSkippedByAnnotator() {
    return false;
  }

  public void setShouldBeSkippedByAnnotator(boolean value) {}  

  @NotNull
  public IntentionAction[] getFixes() {
    return IntentionAction.EMPTY_ARRAY;
  }

  protected XmlAttribute getAttribute() {
    return myAttribute;
  }

  @Nullable
  public String getCanonicalRepresentationText() {
    final AntElement element = getElement();
    final String value = getCanonicalText();
    if( element instanceof AntStructuredElement) {
      return ((AntStructuredElement)element).computeAttributeValue(value);
    }
    return element.getAntProject().computeAttributeValue(value);
  }

  @Override
  public PsiElement resolveInner() {
    throw new UnsupportedOperationException();
  }

  public String getUnresolvedMessagePattern() {
    return CodeInsightBundle.message("error.cannot.resolve.default.message");
  }
}