// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpockUnrollReferenceProvider extends PsiReferenceProvider {

  private static final Pattern PATTERN = Pattern.compile("\\#([\\w_]+)");
  @NlsSafe private static final String UNROLL = "Unroll";
  @NlsSafe private static final String SPOCK_LANG_UNROLL = "spock.lang.Unroll";

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    GrAnnotationNameValuePair nvp = (GrAnnotationNameValuePair)element.getParent();

    String name = nvp.getName();
    if (name != null && !name.equals("value")) return PsiReference.EMPTY_ARRAY;

    PsiElement argumentList = nvp.getParent();
    if (!(argumentList instanceof GrAnnotationArgumentList)) return PsiReference.EMPTY_ARRAY;

    PsiElement eAnnotation = argumentList.getParent();
    if (!(eAnnotation instanceof GrAnnotation annotation)) return PsiReference.EMPTY_ARRAY;

    String shortName = annotation.getShortName();
    if (!shortName.equals(UNROLL) && !shortName.equals(SPOCK_LANG_UNROLL)) return PsiReference.EMPTY_ARRAY;

    PsiElement modifierList = annotation.getParent();
    if (!(modifierList instanceof GrModifierList)) return PsiReference.EMPTY_ARRAY;

    PsiElement eMethod = modifierList.getParent();
    if (!(eMethod instanceof GrMethod method)) return PsiReference.EMPTY_ARRAY;

    TextRange rangeInElement = ElementManipulators.getValueTextRange(element);

    String text = rangeInElement.substring(element.getText());

    final List<SpockVariableReference> references = new ArrayList<>();

    Matcher matcher = PATTERN.matcher(text);
    while (matcher.find()) {
      TextRange range = new TextRange(rangeInElement.getStartOffset() + matcher.start(1), rangeInElement.getStartOffset() + matcher.end(1));

      references.add(new SpockVariableReference(element, range, references, method));
    }

    return references.toArray(PsiReference.EMPTY_ARRAY);
  }

  private static class SpockVariableReference extends PsiReferenceBase<PsiElement> {

    private final PsiElement myLeafElement;
    private final List<? extends SpockVariableReference> myReferences;
    private final GrMethod myMethod;

    SpockVariableReference(PsiElement element, TextRange range, List<? extends SpockVariableReference> references, GrMethod method) {
      super(element, range);
      myReferences = references;
      myMethod = method;
      myLeafElement = element.getFirstChild();
    }

    @Override
    public PsiElement resolve() {
      Map<String, SpockVariableDescriptor> variableMap = SpockUtils.getVariableMap(myMethod);
      String value = getValue();
      SpockVariableDescriptor descriptor = variableMap.get(value);
      if (descriptor == null) return null;
      return descriptor.getVariable();
    }

    @Override
    public Object @NotNull [] getVariants() {
      Map<String, SpockVariableDescriptor> variableMap = SpockUtils.getVariableMap(myMethod);

      Object[] res = new Object[variableMap.size()];

      int i = 0;
      for (SpockVariableDescriptor descriptor : variableMap.values()) {
        res[i++] = descriptor.getVariable();
      }

      return res;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
      if (getElement().getFirstChild() != myLeafElement) { // Element already renamed.
        return getElement();
      }

      String oldValue = getValue();

      PsiElement res = null;

      for (int i = myReferences.size(); --i >= 0; ) {
        SpockVariableReference reference = myReferences.get(i);

        if (oldValue.equals(reference.getCanonicalText())) {
          res = reference.superHandleRename(newElementName);
        }
      }

      return res;
    }

    public PsiElement superHandleRename(String newName) {
      return super.handleElementRename(newName);
    }
  }
}
