/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.bodies;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFieldImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author: Dmitry.Krasilschikov, ilyas
 * @date: 04.05.2007
 */
public class GrTypeDefinitionBodyImpl extends GroovyPsiElementImpl implements GrTypeDefinitionBody {
  private GrField[] myFields;
  public GrTypeDefinitionBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myFields = null;
    for (GrField field : getFields()) {
      ((GrFieldImpl) field).clearCaches();
    }
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTypeDefinitionBody(this);
  }

  public String toString() {
    return "Type definition body";
  }

  public GrField[] getFields() {
    if (myFields == null) {
      GrVariableDeclaration[] declarations = findChildrenByClass(GrVariableDeclaration.class);
      if (declarations.length == 0) return GrField.EMPTY_ARRAY;
      List<GrField> result = new ArrayList<GrField>();
      for (GrVariableDeclaration declaration : declarations) {
        GrVariable[] variables = declaration.getVariables();
        for (GrVariable variable : variables) {
          if (variable instanceof GrField) {
            result.add((GrField) variable);
          }
        }
      }
      myFields = result.toArray(new GrField[result.size()]);
    }

    return myFields;
  }

  public GrMethod[] getGroovyMethods() {
    return findChildrenByClass(GrMethod.class);
  }

  public List<PsiMethod> getMethods() {
    PsiMethod[] groovyMethods = getGroovyMethods();
    GrField[] fields = getFields();
    if (fields.length == 0) return Arrays.asList(groovyMethods);
    List<PsiMethod> result = new ArrayList<PsiMethod>();
    ContainerUtil.addAll(result, groovyMethods);
    for (GrField field : fields) {
      if (field.isProperty()) {
        PsiMethod[] getters = field.getGetters();
        if (getters.length > 0) ContainerUtil.addAll(result, getters);
        PsiMethod setter = field.getSetter();
        if (setter != null) result.add(setter);
      }
    }

    return result;
  }

  public GrMembersDeclaration[] getMemberDeclarations() {
    return findChildrenByClass(GrMembersDeclaration.class);
  }

  @Nullable
  public PsiElement getLBrace() {
    return findChildByType(GroovyTokenTypes.mLCURLY);
  }

  @Nullable
  public PsiElement getRBrace() {
    return findChildByType(GroovyTokenTypes.mRCURLY);
  }

  @NotNull
  public GrClassInitializer[] getInitializers() {
    return findChildrenByClass(GrClassInitializer.class);
  }

  public PsiClass[] getInnerClasses() {
    return findChildrenByClass(PsiClass.class);
  }


  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    PsiElement rBrace = getRBrace();
    if (anchor == null && rBrace == null) {
      throw new IncorrectOperationException();
    }

    if (anchor != null && !this.equals(anchor.getParent())) {
      throw new IncorrectOperationException();
    }

    ASTNode elemNode = declaration.getNode();
    final ASTNode anchorNode = anchor != null ? anchor.getNode() : rBrace.getNode();
    getNode().addChild(elemNode, anchorNode);
    getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchorNode);
    return (GrVariableDeclaration) elemNode.getPsi();
  }
}
