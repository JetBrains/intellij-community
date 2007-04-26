/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import static org.jetbrains.plugins.groovy.lang.psi.impl.types.GrReferenceElementImpl.ReferenceKind.*;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassResolver;

import java.util.List;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrReferenceElementImpl extends GroovyPsiElementImpl implements GrReferenceElement, PsiReference {
  public GrReferenceElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Reference element";
  }

  public GrReferenceElement getQualifier() {
    return (GrReferenceElement) findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  public PsiReference getReference() {
    return this;
  }

  public String getReferenceName() {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      return nameElement.getText();
    }
    return null;
  }

  private PsiElement getReferenceNameElement() {
    return findChildByType(GroovyTokenTypes.mIDENT);
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  enum ReferenceKind {
    CLASS,
    CLASS_OR_PACKAGE,
    PACKAGE_FQ,
    CLASS_OR_PACKAGE_FQ
  }

  @Nullable
  public PsiElement resolve() {
    String refName = getReferenceName();
    if (refName == null) return null;
    switch (getKind()) {
      case CLASS_OR_PACKAGE_FQ: {
        PsiClass aClass = getManager().findClass(PsiUtil.getQualifiedReferenceText(this), getResolveScope());
        if (aClass != null) return aClass;
        //fallthrough
      }

      case PACKAGE_FQ:
        return getManager().findPackage(PsiUtil.getQualifiedReferenceText(this));

      case CLASS:
        GrReferenceElement qualifier = getQualifier();
        if (qualifier != null) {
          PsiElement qualifierResolved = qualifier.resolve();
          if (qualifierResolved instanceof PsiPackage) {
            PsiClass[] classes = ((PsiPackage) qualifierResolved).getClasses();
            for (final PsiClass aClass : classes) {
              if (refName.equals(aClass.getName())) return aClass;
            }
          } else if (qualifierResolved instanceof PsiClass) {
            return ((PsiClass) qualifierResolved).findInnerClassByName(refName, true);
          } else if (qualifierResolved instanceof GrTypeDefinition) {
            return ((GrTypeDefinition) qualifierResolved).findInnerTypeDefinitionByName(refName, true);
          }
        } else {
          ClassResolver processor = new ClassResolver(refName);
          ResolveUtil.treeWalkUp(this, processor);
          List<PsiNamedElement> candidates = processor.getCandidates();
          return candidates.size() == 1 ? candidates.get(0) : null;
        }
        break;

        //todo other cases

    }

    return null;
  }

  private ReferenceKind getKind() {
    PsiElement parent = getParent();
    if (parent instanceof GrReferenceElement) {
      ReferenceKind parentKind = ((GrReferenceElementImpl) parent).getKind();
      if (parentKind == CLASS) return CLASS_OR_PACKAGE;
      return parentKind;
    } else if (parent instanceof GrPackageDefinition) {
      return PACKAGE_FQ;
    }  else if (parent instanceof GrImportStatement) {
      return CLASS_OR_PACKAGE_FQ;
    }

    return CLASS;
  }

  public String getCanonicalText() {
    PsiElement resolved = resolve();
    if (resolved instanceof GrTypeDefinition) {
      return ((GrTypeDefinition) resolved).getQualifiedName();
    }
    if (resolved instanceof PsiPackage) {
      return ((PsiPackage) resolved).getQualifiedName();
    }
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      ASTNode node = nameElement.getNode();
      ASTNode newNameNode = GroovyElementFactory.getInstance(getProject()).createIdentifierFromText(newElementName).getNode();
      assert newNameNode != null && node != null;
      node.getTreeParent().replaceChild(node, newNameNode);
    }

    return this;
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("NIY");
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof GrTypeDefinition || element instanceof PsiPackage) {
      if (Comparing.equal(((PsiNamedElement) element).getName(), getReferenceName())) {
        return element.equals(resolve());
      }
    }
    return false;
  }

  public Object[] getVariants() {
    switch (getKind()) {
      case CLASS:

        GrReferenceElement qualifier = getQualifier();
        if (qualifier != null) {
          PsiElement qualifierResolved = qualifier.resolve();
          if (qualifierResolved instanceof PsiPackage) {
            return ((PsiPackage) qualifierResolved).getClasses();
          } else if (qualifierResolved instanceof PsiClass) {
            return ((PsiClass) qualifierResolved).getInnerClasses();
          } else if (qualifierResolved instanceof GrTypeDefinition) {
            return ((GrTypeDefinition) qualifierResolved).getInnerTypeDefinitions(true);
          }
        } else {
          ClassResolver processor = new ClassResolver(null);
          ResolveUtil.treeWalkUp(this, processor);
          List<PsiNamedElement> candidates = processor.getCandidates();
          return candidates.toArray(PsiNamedElement.EMPTY_ARRAY);
        }
        break;

        //todo other cases
    }
    return new Object[0];
  }

  public boolean isSoft() {
    return false;
  }
}
