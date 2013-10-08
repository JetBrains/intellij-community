/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.formatter.blocks;

import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;

/**
 * Created by Max Medvedev on 10/7/13
 */
public class GroovyBlockWithRange extends GroovyBlock {

  @NotNull private final TextRange myTextRange;

  public GroovyBlockWithRange(@NotNull ASTNode node,
                              @NotNull Indent indent,
                              @NotNull TextRange range,
                              @Nullable Wrap wrap,
                              @NotNull FormattingContext context) {
    super(node, indent, wrap, context);

    myTextRange = range;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myTextRange;
  }
}
