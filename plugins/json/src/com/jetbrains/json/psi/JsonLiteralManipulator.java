package com.jetbrains.json.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class JsonLiteralManipulator extends AbstractElementManipulator<JsonLiteral> {

  @Override
  public JsonLiteral handleContentChange(JsonLiteral element, TextRange range, String newContent) throws IncorrectOperationException {
    String text = "{\"\":\"" + newContent + "\"}";

    final PsiFile dummy = JsonPsiChangeUtils.createDummyFile(element, text);
    JsonProperty property = PsiTreeUtil.findChildOfType(dummy, JsonProperty.class);
    assert property != null;

    JsonPropertyValue value = property.getPropertyValue();
    assert value instanceof JsonLiteral;

    return (JsonLiteral)element.replace(value);
  }
}
