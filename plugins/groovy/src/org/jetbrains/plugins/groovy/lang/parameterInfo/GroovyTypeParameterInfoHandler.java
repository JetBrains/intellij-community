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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GroovyTypeParameterInfoHandler implements ParameterInfoHandlerWithTabActionSupport<GrTypeArgumentList, PsiTypeParameter, GrTypeElement> {

  private static final Set<Class> ALLOWED_PARENT_CLASSES = ContainerUtil.<Class>newHashSet(GrCodeReferenceElement.class);
  private static final Set<Class> STOP_SEARCHING_CLASSES = ContainerUtil.<Class>newHashSet(GroovyFile.class);

  @NotNull
  @Override
  public GrTypeElement[] getActualParameters(@NotNull GrTypeArgumentList o) {
    return o.getTypeArgumentElements();
  }

  @NotNull
  @Override
  public IElementType getActualParameterDelimiterType() {
    return GroovyTokenTypes.mCOMMA;
  }

  @NotNull
  @Override
  public IElementType getActualParametersRBraceType() {
    return GroovyTokenTypes.mGT;
  }

  @NotNull
  @Override
  public Set<Class> getArgumentListAllowedParentClasses() {
    return ALLOWED_PARENT_CLASSES;
  }

  @NotNull
  @Override
  public Set<? extends Class> getArgListStopSearchClasses() {
    return STOP_SEARCHING_CLASSES;
  }

  @NotNull
  @Override
  public Class<GrTypeArgumentList> getArgumentListClass() {
    return GrTypeArgumentList.class;
  }

  @Override
  public boolean couldShowInLookup() {
    return false;
  }

  @Nullable
  @Override
  public Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
    return null;
  }

  @Nullable
  @Override
  public Object[] getParametersForDocumentation(PsiTypeParameter p, ParameterInfoContext context) {
    return new Object[]{p};
  }

  @Nullable
  @Override
  public GrTypeArgumentList findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
    final GrTypeArgumentList parameterList = ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), GrTypeArgumentList.class);

    if (parameterList != null) {
      if (!(parameterList.getParent() instanceof GrCodeReferenceElement)) return null;
      final GrCodeReferenceElement ref = ((GrCodeReferenceElement)parameterList.getParent());

      final PsiElement resolved = ref.resolve();
      if (!(resolved instanceof PsiTypeParameterListOwner)) return null;

      final PsiTypeParameter[] typeParams = ((PsiTypeParameterListOwner)resolved).getTypeParameters();
      if (typeParams.length == 0) return null;

      context.setItemsToShow(typeParams);
      return parameterList;
    }

    return null;
  }

  @Override
  public void showParameterInfo(@NotNull GrTypeArgumentList element, @NotNull CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
  }

  @Nullable
  @Override
  public GrTypeArgumentList findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
    return ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), GrTypeArgumentList.class);
  }

  @Override
  public void updateParameterInfo(@NotNull GrTypeArgumentList parameterOwner, @NotNull UpdateParameterInfoContext context) {
    int index = ParameterInfoUtils.getCurrentParameterIndex(parameterOwner.getNode(), context.getOffset(), getActualParameterDelimiterType());
    context.setCurrentParameter(index);
    final Object[] objectsToView = context.getObjectsToView();
    context.setHighlightedParameter(index < objectsToView.length && index >= 0 ? (PsiElement)objectsToView[index] : null);
  }

  @Nullable
  @Override
  public String getParameterCloseChars() {
    return ",>";
  }

  @Override
  public boolean tracksParameterIndex() {
    return false;
  }

  @Override
  public void updateUI(PsiTypeParameter p, @NotNull ParameterInfoUIContext context) {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append(p.getName());
    int highlightEndOffset = buffer.length();
    buffer.append(" extends ");
    buffer.append(StringUtil.join(p.getSuperTypes(), new Function<PsiClassType, String>() {
      @Override
      public String fun(final PsiClassType t) {
        return t.getPresentableText();
      }
    }, ", "));

    context.setupUIComponentPresentation(buffer.toString(), 0, highlightEndOffset, false, false, false, context.getDefaultParameterColor());
  }
}
