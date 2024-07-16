// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.jsonpath.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

final class JsonPathIdManipulator extends AbstractElementManipulator<JsonPathId> {
  @Override
  public @NotNull JsonPathId handleContentChange(@NotNull JsonPathId element, @NotNull TextRange range,
                                                 String newContent) throws IncorrectOperationException {
    var replacement = range.replace(element.getText(), newContent);

    ASTNode node = element.getNode();
    ((LeafElement)node.getFirstChildNode()).replaceWithText(replacement);

    return element;
  }
}
