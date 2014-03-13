/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.parameterInfo;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GroovyAnnotationAttributeInfoHandler implements ParameterInfoHandlerWithTabActionSupport<GrAnnotationArgumentList, PsiAnnotationMethod, GrAnnotationNameValuePair> {

  private static final Set<Class> ALLOWED_CLASSES = ContainerUtil.<Class>newHashSet(GrAnnotation.class);
  private static final Set<Class<GroovyFile>> STOP_SEARCHING_CLASSES = Collections.singleton(GroovyFile.class);

  @NotNull
  @Override
  public GrAnnotationNameValuePair[] getActualParameters(@NotNull GrAnnotationArgumentList o) {
    return o.getAttributes();
  }

  @NotNull
  @Override
  public IElementType getActualParameterDelimiterType() {
    return GroovyTokenTypes.mCOMMA;
  }

  @NotNull
  @Override
  public IElementType getActualParametersRBraceType() {
    return GroovyTokenTypes.mRPAREN;
  }

  @NotNull
  @Override
  public Set<Class> getArgumentListAllowedParentClasses() {
    return ALLOWED_CLASSES;
  }

  @NotNull
  @Override
  public Set<? extends Class> getArgListStopSearchClasses() {
    return STOP_SEARCHING_CLASSES;
  }

  @NotNull
  @Override
  public Class<GrAnnotationArgumentList> getArgumentListClass() {
    return GrAnnotationArgumentList.class;
  }

  @Override
  public boolean couldShowInLookup() {
    return true;
  }

  @Override
  public Object[] getParametersForLookup(@NotNull LookupElement item, @NotNull ParameterInfoContext context) {
    Object o = item.getObject();

    if (o instanceof GroovyResolveResult) {
      o = ((GroovyResolveResult)o).getElement();
    }


    if (o instanceof PsiClass && ((PsiClass)o).isAnnotationType()) {
      return extractAnnotationMethodsFromClass((PsiClass)o);
    }
    else {
      return GrAnnotationNameValuePair.EMPTY_ARRAY;
    }
  }

  @NotNull
  private static PsiAnnotationMethod[] extractAnnotationMethodsFromClass(@NotNull PsiClass o) {
    if (o.isAnnotationType()) {
      PsiMethod[] methods = o.getMethods();
      if (methods.length > 0) {
        List<PsiAnnotationMethod> annotationMethods = ContainerUtil.findAll(methods, PsiAnnotationMethod.class);
        return annotationMethods.toArray(new PsiAnnotationMethod[annotationMethods.size()]);
      }
    }
    return PsiAnnotationMethod.EMPTY_ARRAY;
  }

  @Override
  public Object[] getParametersForDocumentation(@NotNull PsiAnnotationMethod method, @NotNull ParameterInfoContext context) {
    return new PsiAnnotationMethod[]{method};
  }

  @Override
  public GrAnnotationArgumentList findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
    return findAnchor(context.getEditor(), context.getFile());
  }

  @Nullable
  private static GrAnnotationArgumentList findAnchor(@NotNull final Editor editor, @NotNull final PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) return null;

    return PsiTreeUtil.getParentOfType(element, GrAnnotationArgumentList.class);
  }

  @Override
  public void showParameterInfo(@NotNull GrAnnotationArgumentList argumentList, @NotNull CreateParameterInfoContext context) {
    final GrAnnotation parent = (GrAnnotation)argumentList.getParent();

    final PsiElement resolved = parent.getClassReference().resolve();
    if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
      final PsiAnnotationMethod[] methods = extractAnnotationMethodsFromClass((PsiClass)resolved);
      context.setItemsToShow(methods);
      context.showHint(argumentList, argumentList.getTextRange().getStartOffset(), this);
      final PsiAnnotationMethod currentMethod = findAnnotationMethod(context.getFile(), context.getEditor());
      if (currentMethod != null) {
        context.setHighlightedElement(currentMethod);
      }
    }
  }

  @Nullable
  private static PsiAnnotationMethod findAnnotationMethod(@NotNull PsiFile file, @NotNull Editor editor) {
    PsiNameValuePair pair = ParameterInfoUtils.findParentOfType(file, inferOffset(editor), PsiNameValuePair.class);
    if (pair == null) return null;
    final PsiReference reference = pair.getReference();
    final PsiElement resolved = reference != null ? reference.resolve() : null;
    return PsiUtil.isAnnotationMethod(resolved) ? (PsiAnnotationMethod)resolved : null;
  }

  @Override
  public GrAnnotationArgumentList findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
    return findAnchor(context.getEditor(), context.getFile());
  }

  @Override
  public void updateParameterInfo(@NotNull GrAnnotationArgumentList parameterOwner, @NotNull UpdateParameterInfoContext context) {
    context.setHighlightedParameter(findAnnotationMethod(context.getFile(), context.getEditor()));
  }

  private static int inferOffset(@NotNull final Editor editor) {
    CharSequence chars = editor.getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftForward(chars, editor.getCaretModel().getOffset(), " \t");
    final char character = chars.charAt(offset1);
    if (character == ',' || character == ')') {
      offset1 = CharArrayUtil.shiftBackward(chars, offset1 - 1, " \t");
    }
    return offset1;
  }

  @Override
  public String getParameterCloseChars() {
    return ParameterInfoUtils.DEFAULT_PARAMETER_CLOSE_CHARS;
  }

  @Override
  public boolean tracksParameterIndex() {
    return true;
  }

  @Override
  public void updateUI(@NotNull PsiAnnotationMethod p, @NotNull ParameterInfoUIContext context) {
    @NonNls StringBuilder buffer = new StringBuilder();
    final PsiType returnType = p.getReturnType();
    assert returnType != null;
    buffer.append(returnType.getPresentableText());
    buffer.append(" ");
    int highlightStartOffset = buffer.length();
    buffer.append(p.getName());
    int highlightEndOffset = buffer.length();
    buffer.append("()");

    final PsiAnnotationMemberValue defaultValue = p.getDefaultValue();
    if (defaultValue != null) {
      buffer.append(" default ");
      buffer.append(defaultValue.getText());
    }


    context.setupUIComponentPresentation(buffer.toString(), highlightStartOffset, highlightEndOffset, false, p.isDeprecated(), false, context.getDefaultParameterColor());
  }
}
