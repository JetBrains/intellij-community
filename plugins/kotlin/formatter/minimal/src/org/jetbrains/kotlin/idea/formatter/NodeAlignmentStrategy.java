// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class NodeAlignmentStrategy extends CommonAlignmentStrategy {

    private static final NodeAlignmentStrategy NULL_STRATEGY = fromTypes(AlignmentStrategy.wrap(null));

    /** @return shared strategy instance that returns <code>null</code> all the time */
    public static NodeAlignmentStrategy getNullStrategy() {
        return NULL_STRATEGY;
    }

    public static NodeAlignmentStrategy fromTypes(AlignmentStrategy strategy) {
        return new AlignmentStrategyWrapper(strategy);
    }

    @Override
    public abstract @Nullable Alignment getAlignment(@NotNull ASTNode node);

    private static class AlignmentStrategyWrapper extends NodeAlignmentStrategy {
        private final AlignmentStrategy internalStrategy;

        public AlignmentStrategyWrapper(@NotNull AlignmentStrategy internalStrategy) {
            this.internalStrategy = internalStrategy;
        }

        @Override
        public @Nullable Alignment getAlignment(@NotNull ASTNode node) {
            ASTNode parent = node.getTreeParent();
            if (parent != null) {
                return internalStrategy.getAlignment(parent.getElementType(), node.getElementType());
            }

            return internalStrategy.getAlignment(node.getElementType());
        }
    }
}
