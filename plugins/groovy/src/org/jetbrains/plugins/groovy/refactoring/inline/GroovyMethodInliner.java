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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public class GroovyMethodInliner implements InlineHandler.Inliner {

  private final GrMethod myMethod;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.inline.GroovyMethodInliner");

  public GroovyMethodInliner(GrMethod method) {
    myMethod = method;
  }

  @Nullable
  public Collection<String> getConflicts(PsiReference reference, PsiElement referenced) {
    return new ArrayList<String>();
  }

  public void inlineReference(PsiReference reference, PsiElement referenced) {
    PsiElement element = reference.getElement();
    assert element instanceof GrExpression && element.getParent() instanceof GrCallExpression;
    GrCallExpression call = (GrCallExpression) element.getParent();
    if (isOnExpressionPlace(call)) {
      replaceMethodCall(call, myMethod);
    }
  }

  private static boolean isOnExpressionPlace(GrExpression call) {
    PsiElement parent = call.getParent();
    if (!(parent instanceof GrVariableDeclarationOwner)) {
      return true;
    }

    // todo check tail calls in methods and closures
    GrVariableDeclarationOwner owner = (GrVariableDeclarationOwner) parent;
    return false;
  }


  static GrExpression getSingleExpression(GrMethod method) {
    GrOpenBlock body = method.getBlock();
    assert body != null;
    GrStatement[] statements = body.getStatements();
    if (statements.length != 1) return null;
    if (statements[0] instanceof GrExpression) {
      return ((GrExpression) statements[0]);
    }
    if (statements[0] instanceof GrReturnStatement) {
      return ((GrReturnStatement) statements[0]).getReturnValue();
    }
    return null;
  }

  static PsiElement replaceMethodCall(GrCallExpression call, GrMethod method) {
    try {
      GrMethod newMethod = prepareNewMethod(call, method);
      GrExpression result = getAloneResultExpression(newMethod);
      if (result != null) {
        return call.replaceWithExpression(result, true);
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  /*
  Parepare remporary method sith non-conflicting local names
  */
  private static GrMethod prepareNewMethod(GrCallExpression call, GrMethod method) throws IncorrectOperationException {
    GroovyElementFactory factory = GroovyElementFactory.getInstance(method.getProject());
    GrMethod newMethod = factory.createMethodFromText(method.getText());
    ArrayList<GrVariable> innerVariables = new ArrayList<GrVariable>();
    collectInnerVariables(newMethod.getBlock(), innerVariables);
    // there are only local variables and parameters (possible of inner closures)
    for (GrVariable variable : innerVariables) {
      String newName = InlineMethodConflictSolver.suggestNewName(variable, method, call);
      if (!newName.equals(variable.getName())) {
        variable.setName(newName);
      }
    }
    GroovyRefactoringUtil.replaceParamatersWithArguments(call, newMethod);
    return newMethod;
  }


  private static void collectInnerVariables(PsiElement element, ArrayList<GrVariable> variables) {
    if (element == null) return;
    for (PsiElement child : element.getChildren()) {
      if (child instanceof GrVariable && !(child instanceof GrParameter)) {
        variables.add(((GrVariable) child));
      }
      if (!(child instanceof GrClosableBlock)) {
        collectInnerVariables(child, variables);
      }
    }
  }

  static GrExpression getAloneResultExpression(GrMethod method) {
    GrOpenBlock body = method.getBlock();
    assert body != null;
    GrStatement[] statements = body.getStatements();
    if (statements.length == 1) {
      if (statements[0] instanceof GrExpression) return ((GrExpression) statements[0]);
      if (statements[0] instanceof GrReturnStatement) return ((GrReturnStatement) statements[0]).getReturnValue();
    }
    return null;
  }


}
