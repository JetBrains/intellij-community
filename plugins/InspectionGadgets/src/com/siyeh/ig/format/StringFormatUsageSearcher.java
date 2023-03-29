// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.format;

import com.intellij.find.usages.api.*;
import com.intellij.model.Pointer;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolReferenceHints;
import com.intellij.model.psi.PsiSymbolReferenceService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class StringFormatUsageSearcher implements UsageSearcher {
  @NotNull
  @Override
  public Collection<? extends Usage> collectImmediateResults(@NotNull UsageSearchParameters parameters) {
    SearchTarget target = parameters.getTarget();
    if (target instanceof StringFormatSymbolReferenceProvider.JavaFormatArgumentSymbol symbol) {
      PsiExpression expression = symbol.getFormatString();
      if (expression == null) return List.of();
      PsiSymbolReferenceHints hints = new PsiSymbolReferenceHints() {
        @Override
        public @NotNull Symbol getTarget() {
          return symbol;
        }
      };
      PsiExpression arg = symbol.getExpression();
      PsiSymbolReferenceService service = PsiSymbolReferenceService.getService();
      return SyntaxTraverser.psiTraverser(expression)
        .traverse()
        .flatMap(e -> service.getReferences(e, hints))
        .filter(ref -> ref.resolvesTo(symbol))
        .<Usage>map(RefUsage::new)
        .append(ContainerUtil.createMaybeSingletonList(arg == null ? null : new DefUsage(arg)))
        .toList();
    }
    return List.of();
  }

  private static class RefUsage implements PsiUsage {
    private final PsiSymbolReference myReference;

    private RefUsage(@NotNull PsiSymbolReference reference) {
      myReference = reference;
    }

    @NotNull
    @Override
    public Pointer<? extends PsiUsage> createPointer() {
      return Pointer.hardPointer(this);
    }

    @NotNull
    @Override
    public PsiFile getFile() {
      return myReference.getElement().getContainingFile();
    }

    @NotNull
    @Override
    public TextRange getRange() {
      return myReference.getAbsoluteRange();
    }

    @Override
    public boolean getDeclaration() {
      return false;
    }
  }

  private static class DefUsage implements PsiUsage {
    private final @NotNull PsiExpression myArg;

    private DefUsage(@NotNull PsiExpression arg) {
      myArg = arg;
    }

    @NotNull
    @Override
    public Pointer<? extends PsiUsage> createPointer() {
      return Pointer.hardPointer(this);
    }

    @NotNull
    @Override
    public PsiFile getFile() {
      return myArg.getContainingFile();
    }

    @NotNull
    @Override
    public TextRange getRange() {
      return myArg.getTextRange();
    }

    @Override
    public boolean getDeclaration() {
      return true;
    }
  }
}
