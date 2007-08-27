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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

import java.util.Iterator;

/**
 * @author ilyas
 */
public abstract class GroovyPsiElementImpl extends ASTWrapperPsiElement implements GroovyPsiElement {

  public GroovyPsiElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void removeStatement() throws IncorrectOperationException {
    if (getParent() == null ||
        getParent().getNode() == null) {
      throw new IncorrectOperationException();
    }
    ASTNode parentNode = getParent().getNode();
    parentNode.removeChild(this.getNode());
  }

  public GrStatement replaceWithStatement(@NotNull GrStatement newStmt) throws IncorrectOperationException {
    if (getParent() == null || getParent().getNode() == null) {
      throw new IncorrectOperationException();
    }
    ASTNode parentNode = getParent().getNode();
    ASTNode newNode = newStmt.getNode();
    parentNode.replaceChild(this.getNode(), newNode);
    if (!(newNode.getPsi() instanceof GrStatement)) {
      throw new IncorrectOperationException();
    }
    return (GrStatement) newNode.getPsi();
  }

  public <T extends GroovyPsiElement> Iterable<T> childrenOfType(final TokenSet tokSet) {
    return new Iterable<T>() {

      public Iterator<T> iterator() {
        return new Iterator<T>() {
          private ASTNode findChild(ASTNode child) {
            if (child == null) return null;

            if (tokSet.contains(child.getElementType())) return child;

            return findChild(child.getTreeNext());
          }

          PsiElement first = getFirstChild();

          ASTNode n = first == null ? null : findChild(first.getNode());

          public boolean hasNext() {
            return n != null;
          }

          public T next() {
            if (n == null) return null;
            else {
              final ASTNode res = n;
              n = findChild(n.getTreeNext());
              return (T) res.getPsi();
            }
          }

          public void remove() {
          }
        };
      }
    };
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public void acceptChildren(GroovyElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement) child).accept(visitor);
      }

      child = child.getNextSibling();
    }
  }
}
