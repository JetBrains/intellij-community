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

import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class ClosureBodyBlock extends GroovyBlock {
  private TextRange myTextRange;

  public ClosureBodyBlock(@NotNull ASTNode node,
                          @NotNull Indent indent,
                          @Nullable Wrap wrap,
                          FormattingContext context) {
    super(node, indent, wrap, context);
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    init();
    return myTextRange;
  }

  private void init() {
    if (mySubBlocks == null) {
      GroovyBlockGenerator generator = new GroovyBlockGenerator(this);
      List<ASTNode> children = GroovyBlockGenerator.getClosureBodyVisibleChildren(myNode.getTreeParent());

      mySubBlocks = generator.generateSubBlockForCodeBlocks(false, children, myContext.getGroovySettings().INDENT_LABEL_BLOCKS);

      //at least -> exists
      assert !mySubBlocks.isEmpty();
      TextRange firstRange = mySubBlocks.get(0).getTextRange();
      TextRange lastRange = mySubBlocks.get(mySubBlocks.size() - 1).getTextRange();
      myTextRange = new TextRange(firstRange.getStartOffset(), lastRange.getEndOffset());
    }
  }

  @NotNull
  @Override
  public List<Block> getSubBlocks() {
    init();
    return mySubBlocks;
  }

  @NotNull
  @Override
  public ChildAttributes getChildAttributes(int newChildIndex) {
    return new ChildAttributes(Indent.getNormalIndent(), null);
  }

  @Override
  public boolean isIncomplete() {
    return true;
  }
}
