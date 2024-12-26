// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineASTTransformationPerformer;
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineASTTransformationPerformerEx;
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineTransformationUtilKt;

@FunctionalInterface
public interface GroovyBlockProducer {

  GroovyBlockProducer DEFAULT = (node, indent, wrap, context) -> {
    PsiElement psi = node.getPsi();
    if (!(psi instanceof GroovyPsiElement)) {
      return new GroovyBlock(node, indent, wrap, context);
    }
    if (!DumbService.isDumb(node.getPsi().getProject())) {
      GroovyInlineASTTransformationPerformer performer = GroovyInlineTransformationUtilKt.getRootInlineTransformationPerformer((GroovyPsiElement)psi);
      if (performer instanceof GroovyInlineASTTransformationPerformerEx) {
        return ((GroovyInlineASTTransformationPerformerEx)performer).computeFormattingBlock(node, context);
      }
    }
    return new GroovyBlock(node, indent, wrap, context);
  };

  Block generateBlock(final @NotNull ASTNode node,
                      final @NotNull Indent indent,
                      final @Nullable Wrap wrap,
                      @NotNull FormattingContext context);
}
