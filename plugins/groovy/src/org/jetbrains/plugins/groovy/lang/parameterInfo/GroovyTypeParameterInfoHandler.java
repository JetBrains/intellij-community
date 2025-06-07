// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parameterInfo;

import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.tree.IElementType;
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
public final class GroovyTypeParameterInfoHandler implements ParameterInfoHandlerWithTabActionSupport<GrTypeArgumentList, PsiTypeParameter, GrTypeElement> {

  private static final Set<Class<?>> ALLOWED_PARENT_CLASSES = ContainerUtil.newHashSet(GrCodeReferenceElement.class);
  private static final Set<Class<?>> STOP_SEARCHING_CLASSES = ContainerUtil.newHashSet(GroovyFile.class);

  @Override
  public GrTypeElement @NotNull [] getActualParameters(@NotNull GrTypeArgumentList o) {
    return o.getTypeArgumentElements();
  }

  @Override
  public @NotNull IElementType getActualParameterDelimiterType() {
    return GroovyTokenTypes.mCOMMA;
  }

  @Override
  public @NotNull IElementType getActualParametersRBraceType() {
    return GroovyTokenTypes.mGT;
  }

  @Override
  public @NotNull Set<Class<?>> getArgumentListAllowedParentClasses() {
    return ALLOWED_PARENT_CLASSES;
  }

  @Override
  public @NotNull Set<? extends Class<?>> getArgListStopSearchClasses() {
    return STOP_SEARCHING_CLASSES;
  }

  @Override
  public @NotNull Class<GrTypeArgumentList> getArgumentListClass() {
    return GrTypeArgumentList.class;
  }

  @Override
  public @Nullable GrTypeArgumentList findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
    final GrTypeArgumentList parameterList = ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), GrTypeArgumentList.class);

    if (parameterList != null) {
      if (!(parameterList.getParent() instanceof GrCodeReferenceElement ref)) return null;

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

  @Override
  public @Nullable GrTypeArgumentList findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
    return ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), GrTypeArgumentList.class);
  }

  @Override
  public void updateParameterInfo(@NotNull GrTypeArgumentList parameterOwner, @NotNull UpdateParameterInfoContext context) {
    int index = ParameterInfoUtils.getCurrentParameterIndex(parameterOwner.getNode(), context.getOffset(), getActualParameterDelimiterType());
    context.setCurrentParameter(index);
    final Object[] objectsToView = context.getObjectsToView();
    context.setHighlightedParameter(index < objectsToView.length && index >= 0 ? objectsToView[index] : null);
  }

  @Override
  public void updateUI(PsiTypeParameter p, @NotNull ParameterInfoUIContext context) {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append(p.getName());
    int highlightEndOffset = buffer.length();
    buffer.append(" extends ");
    buffer.append(StringUtil.join(p.getSuperTypes(), t -> t.getPresentableText(), ", "));

    context.setupUIComponentPresentation(buffer.toString(), 0, highlightEndOffset, false, false, false, context.getDefaultParameterColor());
  }
}
