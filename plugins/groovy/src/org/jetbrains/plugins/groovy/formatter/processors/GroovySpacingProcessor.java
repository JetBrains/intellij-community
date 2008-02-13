/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.formatter.processors;

import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import static org.jetbrains.plugins.groovy.GroovyFileType.GROOVY_LANGUAGE;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;

/**
 * @author ilyas
 */
public class GroovySpacingProcessor extends GroovyPsiElementVisitor {

  private static final ThreadLocal<GroovySpacingProcessor> mySharedProcessorAllocator = new ThreadLocal<GroovySpacingProcessor>();
  protected MyGroovySpacingVisitor myGroovyElementVisitor;

  private GroovySpacingProcessor(GroovyElementVisitor visitor) {
    super(visitor);
  }

  public static Spacing getSpacing(ASTNode node, CodeStyleSettings settings) {
    GroovySpacingProcessor spacingProcessor = mySharedProcessorAllocator.get();
    try {
      if (spacingProcessor == null) {
        spacingProcessor = new GroovySpacingProcessor(new MyGroovySpacingVisitor(node, settings));
        mySharedProcessorAllocator.set(spacingProcessor);
      }
      spacingProcessor.doInit(node, settings);
      return spacingProcessor.getResult();
    }
    finally {
      if (spacingProcessor != null) {
        spacingProcessor.clear();
      }
    }
  }

  private void doInit(ASTNode node, CodeStyleSettings settings) {
    myGroovyElementVisitor.doInit();
  }

  private void clear() {
    myGroovyElementVisitor.clear();
  }

  private Spacing getResult() {
    return myGroovyElementVisitor.getResult();
  }


  /**
   * Visitor to adjust spaces via user Code Style Settings
   */
  private static class MyGroovySpacingVisitor extends GroovyElementVisitor {
    private PsiElement myParent;
    private CodeStyleSettings mySettings;

    private Spacing myResult;
    private ASTNode myChild1;
    private ASTNode myChild2;

    public MyGroovySpacingVisitor(ASTNode node, CodeStyleSettings settings) {
      mySettings = settings;
      init(node);
    }

    private void init(final ASTNode child) {
      if (child == null) return;
      ASTNode treePrev = child.getTreePrev();
      while (treePrev != null && SpacingUtil.isWhiteSpace(treePrev)) {
        treePrev = treePrev.getTreePrev();
      }
      if (treePrev == null) {
        init(child.getTreeParent());
      } else {
        myChild2 = child;
        myChild1 = treePrev;
        final CompositeElement parent = (CompositeElement) treePrev.getTreeParent();
        myParent = SourceTreeToPsiMap.treeElementToPsi(parent);
      }
    }

    /*
    Method to start visiting
     */
    private void doInit() {
      if (myChild1.getPsi().getLanguage() != GROOVY_LANGUAGE ||
          myChild2.getPsi().getLanguage() != GROOVY_LANGUAGE) {
        return;
      }

      if (myChild2 != null && mySettings.KEEP_FIRST_COLUMN_COMMENT && SpacingUtil.COMMENT_BIT_SET.contains(myChild2.getElementType())) {
        myResult = Spacing.createKeepingFirstColumnSpacing(0, Integer.MAX_VALUE, true, 1);
      } else {
        if (myParent instanceof GroovyPsiElement) {
          ((GroovyPsiElement) myParent).accept(this);
          if (myResult == null) {
            final ASTNode prev = SpacingUtil.getPrevElementType(myChild2);
            if (prev != null && prev.getElementType() == GroovyTokenTypes.mSL_COMMENT) {
              myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
            } else if (!CodeEditUtil.canStickChildrenTogether(myChild1, myChild2)) {
              myResult = Spacing.createSpacing(1, Integer.MIN_VALUE, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
            } else if (myChild1.getElementType() == GroovyTokenTypes.mML_COMMENT) {
              myResult = null;
            } else if (!SpacingUtil.shouldKeepSpace(myParent)) {
              myResult = Spacing.createSpacing(0, 0, 0, true, mySettings.KEEP_BLANK_LINES_IN_CODE);
            }
          }
        }
      }
    }

    protected void clear() {
      myResult = null;
      myChild2 = myChild1 = null;
      myParent = null;
    }

    protected Spacing getResult() {
      final Spacing result = myResult;
      clear();
      return result;
    }
  }

}

