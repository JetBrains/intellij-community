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
import com.intellij.psi.impl.source.tree.CompositeElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;

/**
 * @author ilyas
 */
public class GroovySpacingProcessor extends GroovyPsiElementVisitor {

  private static final ThreadLocal<GroovySpacingProcessor> mySharedProcessorAllocator = new ThreadLocal<GroovySpacingProcessor>();
  protected MyGroovySpacingVisitor myGroovyElementVisitor;

  public GroovySpacingProcessor(GroovyElementVisitor visitor) {
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
    myGroovyElementVisitor.doInit(node, settings);
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
    private void doInit(final ASTNode child, final CodeStyleSettings settings) {
    }

    public MyGroovySpacingVisitor() {

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

