/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.GroovyBlock;
import org.jetbrains.plugins.groovy.formatter.models.BlockedIndent;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

/**
 * @author Ilya.Sergey
 */
public class GroovyIndentProcessor implements GroovyElementTypes {

  /**
   * Calculates indent, based on code style, between parent block and child node
   *
   * @param parent        parent block
   * @param child         child node
   * @param prevChildNode previous child node
   * @return indent
   */
  @NotNull
  public static Indent getChildIndent(@NotNull final GroovyBlock parent, @Nullable final ASTNode prevChildNode, @NotNull final ASTNode child) {
    final PsiElement psiParent = parent.getNode().getPsi();
    final PsiElement psiChild = child.getPsi();

    if (psiParent instanceof GroovyFile) {
      return Indent.getNoneIndent();
    }

    if (psiParent instanceof BlockedIndent) {
      if (mLCURLY.equals(child.getElementType()) || mRCURLY.equals(child.getElementType())) {
        return Indent.getNoneIndent();
      } else if (psiParent instanceof GrCaseBlock) { // Inside case block
        if (psiChild instanceof GrCaseLabel) {
          return Indent.getNormalIndent();
        } else {
          return Indent.getNormalIndent(); //Indent.getSpaceIndent(4);
        }
      } else {
        return Indent.getNormalIndent();
      }
    }
    /********  Default Indent *********/
    return Indent.getNoneIndent();
  }


}
