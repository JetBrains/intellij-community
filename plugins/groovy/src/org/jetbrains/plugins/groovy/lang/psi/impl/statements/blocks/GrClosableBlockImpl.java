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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;

/**
 * @author ilyas
 */
public class GrClosableBlockImpl extends GrBlockImpl implements GrClosableBlock {
  private volatile GrParameter mySyntheticItParameter;
  private GrVariable myOwner;

  public GrClosableBlockImpl(@NotNull IElementType type, CharSequence buffer) {
    super(type, buffer);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitClosure(this);
  }

  public boolean processDeclarations(final @NotNull PsiScopeProcessor processor,
                                     final @NotNull ResolveState _state,
                                     final PsiElement lastParent,
                                     final @NotNull PsiElement place) {
    if (lastParent == null) return true;

    ResolveState state = _state.put(ResolverProcessor.RESOLVE_CONTEXT, this);
    if (!super.processDeclarations(processor, state, lastParent, place)) return false;

    PsiElement current = place;
    boolean it_already_processed = false;
    while (current != this && current != null) {
      if (current instanceof GrClosableBlock && !((GrClosableBlock)current).hasParametersSection()) {
        it_already_processed = true;
        break;
      }
      current = current.getParent();
    }

    if (!it_already_processed || hasParametersSection()) {
      for (final PsiParameter parameter : getAllParameters()) {
        if (!ResolveUtil.processElement(processor, parameter, state)) return false;
      }
    }

    if (processor instanceof PropertyResolverProcessor && OWNER_NAME.equals(((PropertyResolverProcessor)processor).getName())) {
      processor.handleEvent(ResolveUtil.DECLARATION_SCOPE_PASSED, this);
    }

    if (!ResolveUtil.processElement(processor, getOwner(), state)) return false;

    final PsiClass closureClass = GroovyPsiManager.getInstance(getProject()).findClassWithCache(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, getResolveScope());
    if (closureClass != null) {
      if (!closureClass.processDeclarations(processor, state, lastParent, place)) return false;

      if (place instanceof GroovyPsiElement &&
          !ResolveUtil
            .processNonCodeMethods(GrClosureType.create(this, false /*if it is 'true' need-to-prevent-recursion triggers*/), processor,
                                   (GroovyPsiElement)place, state)) {
        return false;
      }
    }

    return true;
  }

  public String toString() {
    return "Closable block";
  }

  public GrParameter[] getParameters() {
    if (hasParametersSection()) {
      GrParameterListImpl parameterList = getParameterList();
      return parameterList.getParameters();
    }

    return GrParameter.EMPTY_ARRAY;
  }

  public GrParameter[] getAllParameters() {
    if (hasParametersSection()) return getParameters();
    return new GrParameter[]{getSyntheticItParameter()};
  }

  @Override
  @Nullable
  public PsiElement getArrow() {
    return findPsiChildByType(GroovyTokenTypes.mCLOSABLE_BLOCK_OP);
  }


  @NotNull
  public GrParameterListImpl getParameterList() {
    final GrParameterListImpl childByClass = findChildByClass(GrParameterListImpl.class);
    assert childByClass != null;
    return childByClass;
  }

  public void addParameter(GrParameter parameter) {
    GrParameterList parameterList = getParameterList();
    if (getArrow() == null) {
      ASTNode next = parameterList.getNode().getTreeNext();
      getNode().addLeaf(GroovyTokenTypes.mCLOSABLE_BLOCK_OP, "->", next);
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", next);
    }

    parameterList.addParameterToEnd(parameter);
  }

  public boolean hasParametersSection() {
    return getArrow() != null;
  }

  public PsiType getType() {
    return GrClosureType.create(this, true);
  }

  @Nullable
  public PsiType getNominalType() {
    return getType();
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    mySyntheticItParameter = null;
  }

  public GrParameter getSyntheticItParameter() {
    if (mySyntheticItParameter == null) {
      GrParameter fresh = new ClosureSyntheticParameter(this);
      synchronized (this) {
        if (mySyntheticItParameter == null) {
          mySyntheticItParameter = fresh;
        }
      }
    }
    return mySyntheticItParameter;
  }

  private GrVariable getOwner() {
    if (myOwner == null) {
      final GroovyPsiElement context = PsiTreeUtil.getParentOfType(this, GrTypeDefinition.class, GrClosableBlock.class, GroovyFile.class);
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
      PsiType type = null;
      if (context instanceof GrTypeDefinition) {
        type = factory.createType((PsiClass)context);
      }
      else if (context instanceof GrClosableBlock) {
        type = GrClosureType.create((GrClosableBlock)context, true);
      }
      else if (context instanceof GroovyFile) {
        final PsiClass scriptClass = ((GroovyFile)context).getScriptClass();
        if (scriptClass != null && GroovyNamesUtil.isIdentifier(scriptClass.getName())) type = factory.createType(scriptClass);
      }
      if (type == null) {
        type = TypesUtil.getJavaLangObject(this);
      }

      myOwner = GroovyPsiElementFactory.getInstance(getProject()).createVariableDeclaration(null, null, type, OWNER_NAME).getVariables()[0];
    }

    return myOwner;
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
  }

  private static final Function<GrClosableBlock, PsiType> ourTypesCalculator = new NullableFunction<GrClosableBlock, PsiType>() {
    public PsiType fun(GrClosableBlock block) {
      return GroovyPsiManager.inferType(block, new MethodTypeInferencer(block));
    }
  };

  @Nullable
  public PsiType getReturnType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, ourTypesCalculator);
  }

  @Override
  public void removeStatement() throws IncorrectOperationException {
    GroovyPsiElementImpl.removeStatement(this);
  }
}
