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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;

/**
 * @author ilyas
 */
public class GrClosableBlockImpl extends GrBlockImpl implements GrClosableBlock {

  private volatile GrParameter[] mySyntheticItParameter;

  public GrClosableBlockImpl(@NotNull IElementType type, CharSequence buffer) {
    super(type, buffer);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitClosure(this);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    mySyntheticItParameter = null;
  }

  public boolean processDeclarations(final @NotNull PsiScopeProcessor processor,
                                     final @NotNull ResolveState _state,
                                     final @Nullable PsiElement lastParent,
                                     final @NotNull PsiElement place) {
    if (lastParent == null) return true;

    ResolveState state = _state.put(ResolverProcessor.RESOLVE_CONTEXT, this);
    if (!super.processDeclarations(processor, _state, lastParent, place)) return false;
    if (!processParameters(processor, _state, state, place)) return false;
    if (!ResolveUtil.processElement(processor, getOwner(), _state)) return false;
    if (!processOwnerAndDelegate(processor, state, place)) return false;
    if (!processClosureClassMembers(processor, state, lastParent, place)) return false;

    return true;
  }

  private boolean processOwnerAndDelegate(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, @NotNull PsiElement place) {
    Boolean result = processDelegatesTo(processor, state, place);
    if (result != null) return result.booleanValue();

    if (!processOwner(processor, state)) return false;
    return true;
  }

  @Nullable
  private Boolean processDelegatesTo(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, @NotNull PsiElement place) {
    final GrCall call = getContainingCall();

    if (call == null) return null;

    final Map<GrExpression, Pair<PsiParameter, PsiType>> map = mapArgumentsToParameters(place, call);
    if (map == null) {
      return null;
    }

    final Pair<PsiParameter, PsiType> pair = map.get(this);
    final PsiParameter parameter = pair.getFirst();
    final PsiModifierList modifierList = parameter.getModifierList();
    if (modifierList == null) return null;

    final PsiAnnotation annotation = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO);
    if (annotation == null) return null;

    final PsiAnnotationMemberValue strategy = annotation.findAttributeValue("strategy");

    switch (getStrategyValue(strategy)) {
      case 0:
        if (!processOwner(processor, state)) return false;
        if (!processDelegate(annotation, processor, state, place)) return false;
        return true;
      case 1:
        if (!processDelegate(annotation, processor, state, place)) return false;
        if (!processOwner(processor, state)) return false;
        return true;
      case 2:
        if (!processOwner(processor, state)) return false;
        return true;
      case 3:
        if (!processDelegate(annotation, processor, state, place)) return false;
        return true;
      case 4:
        return true;
      default:
        return null;
    }
  }

  @Nullable
  private static Map<GrExpression, Pair<PsiParameter, PsiType>> mapArgumentsToParameters(@NotNull PsiElement place, @NotNull GrCall call) {
    GroovyResolveResult result = GroovyResolveResult.EMPTY_RESULT;

    if (call instanceof GrMethodCall) {
      final GrExpression invoked = ((GrMethodCall)call).getInvokedExpression();
      if (invoked instanceof GrReferenceExpression) {
        final GroovyResolveResult[] results = ((GrReferenceExpression)invoked).resolveByShape();
        if (results.length == 1) {
          result = results[0];
        }
      }
    }
    else {
      result = call.advancedResolve();
    }

    return GrClosureSignatureUtil.mapArgumentsToParameters(
      result, place, false, true, call.getNamedArguments(), call.getExpressionArguments(), call.getClosureArguments()
    );
  }

  private static boolean processDelegate(@NotNull PsiAnnotation delegatesTo,
                                         @NotNull PsiScopeProcessor processor,
                                         @NotNull ResolveState state,
                                         @NotNull PsiElement place) {
    final PsiAnnotationMemberValue value = delegatesTo.findAttributeValue("value");
    if (value instanceof GrReferenceExpression) {
      final PsiType type = extractTypeFromClassType(((GrReferenceExpression)value).getType());
      if (type != null) {
        return ResolveUtil.processAllDeclarations(type, processor, state, place);
      }
    }

    return true;
  }

  @Nullable
  private static PsiType extractTypeFromClassType(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClass resolved = ((PsiClassType)type).resolve();
      if (resolved != null && CommonClassNames.JAVA_LANG_CLASS.equals(resolved.getQualifiedName())) {
        final PsiType[] parameters = ((PsiClassType)type).getParameters();
        if (parameters.length == 1) {
          return parameters[0];
        }
      }
    }
    return null;
  }


  private static int getStrategyValue(@Nullable PsiAnnotationMemberValue strategy) {
    if (strategy == null) return -1;

    final String text = strategy.getText();
    if ("0".equals(text)) return 0;
    if ("1".equals(text)) return 1;
    if ("2".equals(text)) return 2;
    if ("3".equals(text)) return 3;
    if ("4".equals(text)) return 4;

    if (text.endsWith("OWNER_FIRST")) return 0;
    if (text.endsWith("DELEGATE_FIRST")) return 1;
    if (text.endsWith("OWNER_ONLY")) return 2;
    if (text.endsWith("DELEGATE_ONLY")) return 3;
    if (text.endsWith("TO_SELF")) return 4;

    return -1;
  }

  @Nullable
  private GrCall getContainingCall() {
    final PsiElement parent = getParent();
    if (parent instanceof GrCall && ArrayUtil.contains(this, ((GrCall)parent).getClosureArguments())) {
      return (GrCall)parent;
    }
    else if (parent instanceof GrArgumentList) {
      final PsiElement parent1 = parent.getParent();
      if (parent1 instanceof GrCall) {
        return (GrCall)parent1;
      }
    }

    return null;
  }

  private boolean processClosureClassMembers(PsiScopeProcessor processor,
                                             ResolveState state, PsiElement lastParent,
                                             PsiElement place) {
    final PsiClass closureClass = GroovyPsiManager.getInstance(getProject()).findClassWithCache(GROOVY_LANG_CLOSURE, getResolveScope());
    if (closureClass != null) {
      if (!closureClass.processDeclarations(processor, state, lastParent, place)) return false;

      if (place instanceof GroovyPsiElement) {
        GrClosureType closureType = GrClosureType.create(this, false /*if it is 'true' need-to-prevent-recursion triggers*/);
        if (!ResolveUtil.processNonCodeMembers(closureType, processor, place, state)) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean processParameters(PsiScopeProcessor processor,
                                    ResolveState _state,
                                    ResolveState state,
                                    PsiElement place) {
    if (hasParametersSection()) {
      for (GrParameter parameter : getParameters()) {
        if (!ResolveUtil.processElement(processor, parameter, _state)) return false;
      }
    }
    else if (!isItAlreadyDeclared(place)) {
      GrParameter[] synth = getSyntheticItParameter();
      if (synth.length > 0) {
        if (!ResolveUtil.processElement(processor, synth[0], state)) return false;
      }
    }
    return true;
  }

  private boolean processOwner(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state) {
    final PsiElement parent = this.getParent();
    if (parent instanceof GroovyPsiElement) {
      return ResolveUtil.treeWalkUp((GroovyPsiElement)parent, processor, true, state);
    }
    else {
      return true;
    }
  }

  private boolean isItAlreadyDeclared(PsiElement place) {
    while (place != this && place != null) {
      if (place instanceof GrClosableBlock &&
          !((GrClosableBlock)place).hasParametersSection() &&
          !(place.getParent() instanceof GrStringInjection)) {
        return true;
      }
      place = place.getParent();
    }
    return false;
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
    if (getParent() instanceof GrStringInjection) return GrParameter.EMPTY_ARRAY;
    if (hasParametersSection()) return getParameters();
    return getSyntheticItParameter();
  }

  @Override
  @Nullable
  public PsiElement getArrow() {
    return findPsiChildByType(GroovyTokenTypes.mCLOSABLE_BLOCK_OP);
  }

  @Override
  public boolean isVarArgs() {
    return PsiImplUtil.isVarArgs(getParameters());
  }


  @NotNull
  public GrParameterListImpl getParameterList() {
    final GrParameterListImpl childByClass = findChildByClass(GrParameterListImpl.class);
    assert childByClass != null;
    return childByClass;
  }

  public GrParameter addParameter(GrParameter parameter) {
    GrParameterList parameterList = getParameterList();
    if (getArrow() == null) {
      final GrParameterList newParamList = (GrParameterList)addAfter(parameterList, getLBrace());
      parameterList.delete();
      ASTNode next = newParamList.getNode().getTreeNext();
      getNode().addLeaf(GroovyTokenTypes.mCLOSABLE_BLOCK_OP, "->", next);
      return (GrParameter)newParamList.add(parameter);
    }

    return (GrParameter)parameterList.add(parameter);
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

  public GrParameter[] getSyntheticItParameter() {
    if (getParent() instanceof GrStringInjection) {
      return GrParameter.EMPTY_ARRAY;
    }

    GrParameter[] res = mySyntheticItParameter;
    if (res == null) {
      res = new GrParameter[]{new ClosureSyntheticParameter(this)};
      synchronized (this) {
        if (mySyntheticItParameter == null) {
          mySyntheticItParameter = res;
        }
      }
    }

    return res;
  }

  private PsiVariable getOwner() {
    return CachedValuesManager.getManager(getProject()).getCachedValue(this, new CachedValueProvider<PsiVariable>() {
      @Override
      public Result<PsiVariable> compute() {
        final GroovyPsiElement context = PsiTreeUtil.getParentOfType(GrClosableBlockImpl.this, GrTypeDefinition.class, GrClosableBlock.class, GroovyFile.class);
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
          type = TypesUtil.getJavaLangObject(GrClosableBlockImpl.this);
        }

        PsiVariable owner = new GrLightVariable(getManager(), OWNER_NAME, type, GrClosableBlockImpl.this);
        return Result.create(owner, PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
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
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, ourTypesCalculator);
  }

  @Override
  public void removeStatement() throws IncorrectOperationException {
    GroovyPsiElementImpl.removeStatement(this);
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return true;
  }
}
