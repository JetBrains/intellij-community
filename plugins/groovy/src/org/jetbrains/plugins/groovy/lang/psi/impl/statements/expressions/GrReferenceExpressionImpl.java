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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.ResolveKind;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.EnumSet;
import java.util.Map;

/**
 * @author ilyas
 */
public class GrReferenceExpressionImpl extends GrReferenceElementImpl implements GrReferenceExpression {
  public GrReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitReferenceExpression(this);
  }

  public PsiElement getReferenceNameElement() {
    final ASTNode lastChild = getNode().getLastChildNode();
    if (lastChild == null) return null;
    for (IElementType elementType : TokenSets.PROPERTY_NAMES.getTypes()) {
      if (lastChild.getElementType() == elementType) return lastChild.getPsi();
    }

    return null;
  }

  public String getReferenceName() {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      if (nameElement.getNode().getElementType() == GroovyElementTypes.mSTRING_LITERAL)
        return getValueText(nameElement);
      return nameElement.getText();
    }
    return null;
  }

  private String getValueText(PsiElement stringNameElement) {
    final String text = stringNameElement.getText();
    final char firstChar = text.charAt(0);
    if (text.charAt(text.length() - 1) == firstChar) return text.substring(1, text.length() - 1);
    return text.substring(1);
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final PsiElement resolved = resolve();
    if (resolved instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) resolved;
      final String oldName = getReferenceName();
      if (!method.getName().equals(oldName)) { //was property reference to accessor
        if (PropertyUtil.isSimplePropertyAccessor(method)) {
          final String newPropertyName = PropertyUtil.getPropertyName(newElementName);
          if (newPropertyName != null) {
            return doHandleElementRename(newPropertyName);
          } else {
            //todo encapsulate fields:)
          }
        }
      }
    } else if (resolved instanceof GrField && ((GrField) resolved).isProperty()) {
      final GrField field = (GrField) resolved;
      final String oldName = getReferenceName();
      if (!oldName.equals(field.getName())) { //was accessor reference to property
        if (oldName.startsWith("get")) {
          return doHandleElementRename("get" + StringUtil.capitalize(newElementName));
        } else if (oldName.startsWith("set")) {
          return doHandleElementRename("set" + StringUtil.capitalize(newElementName));
        }
      }
    }

    return doHandleElementRename(newElementName);
  }

  private PsiElement doHandleElementRename(String newElementName) throws IncorrectOperationException {
    if (!PsiUtil.isValidReferenceName(newElementName)) {
      PsiElement element = GroovyElementFactory.getInstance(getProject()).createStringLiteral(newElementName);
      getReferenceNameElement().replace(element);
      return this;
    }

    return super.handleElementRename(newElementName);
  }

  public int getTextOffset() {
    PsiElement parent = getParent();
    TextRange range = getTextRange();
    if (!(parent instanceof GrAssignmentExpression) || !this.equals(((GrAssignmentExpression) parent).getLValue())) {
      return range.getEndOffset(); //need this as a hack against TargetElementUtil
    }

    return range.getStartOffset();
  }

  public String toString() {
    return "Reference expression";
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  private static final MyResolver RESOLVER = new MyResolver();

  private static final MyTypesCalculator TYPES_CALCULATOR = new MyTypesCalculator();

  public GrReferenceExpression getElementToCompare() {
    return this;
  }

  public int compareTo(GrReferenceExpression grReferenceExpression) {
    if (this.equals(grReferenceExpression)) return 0;
    else return getText().compareTo(grReferenceExpression.getText());
  }

  private PsiType getNominalType() {
    IElementType dotType = getDotTokenType();
    final GroovyResolveResult resolveResult = advancedResolve();
    PsiElement resolved = resolveResult.getElement();
    PsiType result = null;
    PsiManager manager = getManager();
    if (resolved instanceof PsiClass) {
      if (getParent() instanceof GrReferenceExpression) {
        result = manager.getElementFactory().createType((PsiClass) resolved);
      } else {
        PsiClass javaLangClass = manager.findClass("java.lang.Class", getResolveScope());
        if (javaLangClass != null) {
          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
          final PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
          if (typeParameters.length == 1) {
            substitutor = substitutor.put(typeParameters[0], manager.getElementFactory().createType((PsiClass) resolved));
          }
          result = manager.getElementFactory().createType(javaLangClass, substitutor);
        }
      }
    } else if (resolved instanceof GrVariable) {
      result = ((GrVariable) resolved).getTypeGroovy();
    } else if (resolved instanceof PsiVariable) {
      result = ((PsiVariable) resolved).getType();
    } else
    if (resolved instanceof PsiMethod && !GroovyPsiManager.getInstance(resolved.getProject()).isTypeBeingInferred(resolved)) {
      if (dotType == GroovyTokenTypes.mMEMBER_POINTER) {
        return manager.getElementFactory().createTypeByFQClassName("groovy.lang.Closure", getResolveScope());
      }
      PsiMethod method = (PsiMethod) resolved;
      if (PropertyUtil.isSimplePropertySetter(method)) {
        result = method.getParameterList().getParameters()[0].getType();
      } else {
        result = method.getReturnType();
      }
    } else if (resolved instanceof GrReferenceExpression) {
      PsiElement parent = resolved.getParent();
      if (parent instanceof GrAssignmentExpression) {
        GrAssignmentExpression assignment = (GrAssignmentExpression) parent;
        if (resolved.equals(assignment.getLValue())) {
          GrExpression rValue = assignment.getRValue();
          if (rValue != null) {
            PsiType rType = rValue.getType();
            if (rType != null) result = rType;
          }
        }
      }
    } else if (resolved == null) {
      if ("class".equals(getReferenceName())) {
        return getManager().getElementFactory().createTypeByFQClassName("java.lang.Class",
            getResolveScope());
      }
    }

    if (result != null) {
      result = resolveResult.getSubstitutor().substitute(result);
      result = TypesUtil.boxPrimitiveType(result, manager, getResolveScope());
    }
    if (dotType != GroovyTokenTypes.mSPREAD_DOT) {
      return result;
    } else {
      return ResolveUtil.getListTypeForSpreadOperator(this, result);
    }
  }

  private static final class MyTypesCalculator implements Function<GrReferenceExpressionImpl, PsiType> {
    public PsiType fun(GrReferenceExpressionImpl refExpr) {
      final PsiType inferred = GroovyPsiManager.getInstance(refExpr.getProject()).getTypeInferenceHelper().getInferredType(refExpr);
      if (inferred != null) return inferred;

      return refExpr.getNominalType();
    }
  }

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPES_CALCULATOR);
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr) throws IncorrectOperationException {
    return PsiImplUtil.replaceExpression(this, newExpr);
  }

  public String getName() {
    return getReferenceName();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiElement nameElement = getReferenceNameElement();
    ASTNode node = nameElement.getNode();
    ASTNode newNameNode = GroovyElementFactory.getInstance(getProject()).createReferenceNameFromText(name).getNode();
    assert newNameNode != null && node != null;
    node.getTreeParent().replaceChild(node, newNameNode);

    return this;
  }

  private static class MyResolver implements ResolveCache.PolyVariantResolver<GrReferenceExpressionImpl> {
    public GroovyResolveResult[] resolve(GrReferenceExpressionImpl refExpr, boolean incompleteCode) {
      String name = refExpr.getReferenceName();
      if (name == null) return null;
      ResolverProcessor processor = getMethodOrPropertyResolveProcessor(refExpr, name, false, !incompleteCode);

      resolveImpl(refExpr, processor);

      GroovyResolveResult[] propertyCandidates = processor.getCandidates();
      if (propertyCandidates.length > 0) return propertyCandidates;
      if (refExpr.getKind() == Kind.TYPE_OR_PROPERTY) {
        ResolverProcessor classProcessor = new ResolverProcessor(refExpr.getReferenceName(), EnumSet.of(ResolveKind.CLASS_OR_PACKAGE), refExpr, false, PsiType.EMPTY_ARRAY);
        resolveImpl(refExpr, classProcessor);
        return classProcessor.getCandidates();
      }

      return GroovyResolveResult.EMPTY_ARRAY;
    }

    private void resolveImpl(GrReferenceExpressionImpl refExpr, ResolverProcessor processor) {
      GrExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier == null) {
        ResolveUtil.treeWalkUp(refExpr, processor);
        if (!processor.hasCandidates()) {
          qualifier = PsiImplUtil.getRuntimeQualifier(refExpr);
          if (qualifier != null) {
            processQualifier(refExpr, processor, qualifier);
          }
        }
      } else {
        if (refExpr.getDotTokenType() != GroovyTokenTypes.mSPREAD_DOT) {
          processQualifier(refExpr, processor, qualifier);
        } else {
          processQualifierForSpreadDot(refExpr, processor, qualifier);
        }
      }
    }

    private void processQualifierForSpreadDot(GrReferenceExpressionImpl refExpr, ResolverProcessor processor, GrExpression qualifier) {
      PsiType qualifierType = qualifier.getType();
      if (qualifierType instanceof PsiClassType) {
        PsiClassType.ClassResolveResult result = ((PsiClassType) qualifierType).resolveGenerics();
        PsiClass clazz = result.getElement();
        if (clazz != null) {
          PsiClass listClass = ResolveUtil.findListClass(refExpr.getManager(), refExpr.getResolveScope());
          if (listClass != null && listClass.getTypeParameters().length == 1) {
            PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(clazz, listClass, result.getSubstitutor());
            if (substitutor != null) {
              PsiType componentType = substitutor.substitute(listClass.getTypeParameters()[0]);
              if (componentType != null) {
                processClassQualifierType(refExpr, processor, componentType);
              }
            }
          }
        }
      } else if (qualifierType instanceof PsiArrayType) {
        processClassQualifierType(refExpr, processor, ((PsiArrayType) qualifierType).getComponentType());
      }
    }

    private void processQualifier(GrReferenceExpressionImpl refExpr, ResolverProcessor processor, GrExpression qualifier) {
      PsiType qualifierType = qualifier.getType();
      if (qualifierType == null) {
        if (qualifier instanceof GrReferenceExpression) {
          PsiElement resolved = ((GrReferenceExpression) qualifier).resolve();
          if (resolved instanceof PsiPackage) {
            if (!resolved.processDeclarations(processor, PsiSubstitutor.EMPTY, null, refExpr)) return;
          }
        }
      } else {
        if (qualifierType instanceof PsiIntersectionType) {
          for (PsiType conjunct : ((PsiIntersectionType) qualifierType).getConjuncts()) {
            processClassQualifierType(refExpr, processor, conjunct);
          }
        } else {
          processClassQualifierType(refExpr, processor, qualifierType);
          if (qualifier instanceof GrReferenceExpression) {
            PsiElement resolved = ((GrReferenceExpression) qualifier).resolve();
            if (resolved instanceof PsiClass) { //omitted .class
              PsiClass javaLangClass = PsiUtil.getJavaLangObject(resolved, refExpr.getResolveScope());
              if (javaLangClass != null) {
                javaLangClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, refExpr);
              }
            }
          }
        }
      }
    }

    private void processClassQualifierType(GrReferenceExpressionImpl refExpr, ResolverProcessor processor, PsiType qualifierType) {
      Project project = refExpr.getProject();
      if (qualifierType instanceof PsiClassType) {
        PsiClassType.ClassResolveResult qualifierResult = ((PsiClassType) qualifierType).resolveGenerics();
        PsiClass qualifierClass = qualifierResult.getElement();
        if (qualifierClass != null) {
          if (!qualifierClass.processDeclarations(processor, qualifierResult.getSubstitutor(), null, refExpr)) return;
        }
      } else if (qualifierType instanceof PsiArrayType) {
        final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(project).getArrayClass();
        if (!arrayClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, refExpr)) return;
      } else if (qualifierType instanceof PsiIntersectionType) {
        for (PsiType conjunct : ((PsiIntersectionType) qualifierType).getConjuncts()) {
          processClassQualifierType(refExpr, processor, conjunct);
        }
        return;
      }

      ResolveUtil.processDefaultMethods(qualifierType, processor, project);
    }
  }

  static ResolverProcessor getMethodOrPropertyResolveProcessor(GrReferenceExpression refExpr, String name, boolean forCompletion, boolean checkArguments) {
    Kind kind = ((GrReferenceExpressionImpl) refExpr).getKind();
    ResolverProcessor processor;
    if (kind == Kind.METHOD_OR_PROPERTY) {
      final PsiType[] argTypes = checkArguments ? PsiUtil.getArgumentTypes(refExpr, false) : null;
      processor = new MethodResolverProcessor(name, refExpr, forCompletion, false, argTypes);
    } else {
      processor = new PropertyResolverProcessor(name, refExpr, forCompletion);
    }

    return processor;
  }

  enum Kind {
    PROPERTY,
    TYPE_OR_PROPERTY,
    METHOD_OR_PROPERTY
  }

  Kind getKind() {
    PsiElement parent = getParent();
    if (parent instanceof GrMethodCallExpression || parent instanceof GrApplicationStatement) {
      return Kind.METHOD_OR_PROPERTY;
    } else if (parent instanceof GrStatement || parent instanceof GrCodeBlock) {
      return Kind.TYPE_OR_PROPERTY;
    }

    return Kind.TYPE_OR_PROPERTY;
  }

  public String getCanonicalText() {
    return ""; //todo
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiMethod && PropertyUtil.isSimplePropertyAccessor((PsiMethod) element)) {
      return getManager().areElementsEquivalent(element, resolve());
    }
    if (element instanceof GrField && ((GrField) element).isProperty()) {
      return getManager().areElementsEquivalent(element, resolve());
    } else
    if (element instanceof PsiNamedElement && Comparing.equal(((PsiNamedElement) element).getName(), getReferenceName())) {
      return getManager().areElementsEquivalent(element, resolve());
    }
    return false;
  }

  public Object[] getVariants() {

    return CompleteReferenceExpression.getVariants(this);
  }


  public boolean isSoft() {
    return getQualifierExpression() != null;  //todo rethink
  }

  public GrExpression getQualifierExpression() {
    return findChildByClass(GrExpression.class);
  }

  @Nullable
  public IElementType getDotTokenType() {
    PsiElement dot = findChildByType(GroovyTokenTypes.DOTS);
    return dot == null ? null : dot.getNode().getElementType();
  }

  public GroovyResolveResult advancedResolve() {
    ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public GroovyResolveResult[] multiResolve(boolean incomplete) {  //incomplete means we do not take arguments into consideration
    return (GroovyResolveResult[]) getManager().getResolveCache().resolveWithCaching(this, RESOLVER, false, incomplete);
  }

  @NotNull
  public GroovyResolveResult[] getSameNameVariants() {
    return RESOLVER.resolve(this, true);
  }
}