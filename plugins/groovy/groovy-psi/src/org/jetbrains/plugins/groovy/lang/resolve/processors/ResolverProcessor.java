// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * @author ven
 */
public abstract class ResolverProcessor<T extends GroovyResolveResult> extends GrScopeProcessorWithHints {

  protected final PsiElement myPlace;
  private List<T> myCandidates;

  protected ResolverProcessor(@Nullable String name,
                              @NotNull EnumSet<DeclarationKind> resolveTargets,
                              @NotNull PsiElement place) {
    super(name, resolveTargets);
    myPlace = place;
  }

  protected final void addCandidate(@NotNull T candidate) {
    PsiElement element = candidate.getElement();
    assert element == null || element.isValid() : getElementInfo(element);

    if (myCandidates == null) myCandidates = new ArrayList<>();
    myCandidates.add(candidate);
  }

  @NotNull
  private static String getElementInfo(@NotNull PsiElement element) {
    String text;
    if (element instanceof LightElement) {
      final PsiElement context = element.getContext();
      text = context instanceof LightElement ? context.toString() :
             context != null ? context.getText() : null;
    }
    else {
      text = element.getText();
    }
    return "invalid resolve candidate: " + element.getClass() + ", text: " + text;
  }

  @NotNull
  protected List<T> getCandidatesInternal() {
    return myCandidates == null ? Collections.emptyList() : myCandidates;
  }

  protected boolean isAccessible(@NotNull PsiNamedElement namedElement) {
    if (namedElement instanceof GrField) {
      final GrField field = (GrField)namedElement;
      if (PsiUtil.isAccessible(myPlace, field)) {
        return true;
      }

      for (GrAccessorMethod method : field.getGetters()) {
        if (PsiUtil.isAccessible(myPlace, method)) {
          return true;
        }
      }
      final GrAccessorMethod setter = field.getSetter();
      if (setter != null && PsiUtil.isAccessible(myPlace, setter)) {
        return true;
      }

      return false;
    }

    return !(namedElement instanceof PsiMember) ||
           PsiUtil.isAccessible(myPlace, ((PsiMember)namedElement));
  }

  protected boolean isStaticsOK(@NotNull PsiNamedElement element, @Nullable PsiElement resolveContext, boolean filterStaticAfterInstanceQualifier) {
    if (resolveContext instanceof GrImportStatement) return true;

    if (element instanceof PsiModifierListOwner) {
      return GrStaticChecker.isStaticsOK((PsiModifierListOwner)element, myPlace, resolveContext, filterStaticAfterInstanceQualifier);
    }
    return true;
  }

  @NotNull
  public GroovyResolveResult[] getCandidates() {
    if (myCandidates == null) return GroovyResolveResult.EMPTY_ARRAY;
    return myCandidates.toArray(new GroovyResolveResult[myCandidates.size()]);
  }

  public boolean hasCandidates() {
    return myCandidates != null;
  }

  @Override
  public String toString() {
    return "NameHint: '" +
           myName +
           "', " +
           myResolveTargetKinds +
           ", Candidates: " +
           (myCandidates == null ? 0 : myCandidates.size());
  }
}
