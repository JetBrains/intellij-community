package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntFileReference extends FileReferenceBase implements AntReference {

  public AntFileReference(final AntFileReferenceSet set, final TextRange range, final int index, final String text) {
    super(set, range, index, text);
  }

  @Nullable
  protected String getText() {
    return getElement().computeAttributeValue(super.getText());
  }

  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  public ReferenceType getSoftenType() {
    return new ReferenceType(new int[]{ReferenceType.FILE, ReferenceType.DIRECTORY});
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("file.doesnt.exist", getCanonicalRepresentationText());
  }

  public boolean shouldBeSkippedByAnnotator() {
    return false;
  }

  public void setShouldBeSkippedByAnnotator(boolean value) {
  }

  @NotNull
  public IntentionAction[] getFixes() {
    return EMPTY_INTENTIONS;
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
}