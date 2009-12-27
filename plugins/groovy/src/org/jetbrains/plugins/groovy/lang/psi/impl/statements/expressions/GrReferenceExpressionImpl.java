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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.*;

import java.util.EnumSet;

/**
 * @author ilyas
 */
public class GrReferenceExpressionImpl extends GrReferenceElementImpl implements GrReferenceExpression {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl");

  public GrReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitReferenceExpression(this);
  }

  @Nullable
  public PsiElement getReferenceNameElement() {
    final ASTNode lastChild = getNode().getLastChildNode();
    if (lastChild == null) return null;
    for (IElementType elementType : TokenSets.REFERENCE_NAMES.getTypes()) {
      if (lastChild.getElementType() == elementType) return lastChild.getPsi();
    }

    return null;
  }

  public PsiReference getReference() {
    PsiReference[] otherReferences = ReferenceProvidersRegistry.getReferencesFromProviders(this, GrReferenceExpression.class);
    PsiReference[] thisReference = {this};
    return new PsiMultiReference(otherReferences.length == 0 ? thisReference : ArrayUtil.mergeArrays(thisReference, otherReferences, PsiReference.class), this);
  }

  @Nullable
  public PsiElement getQualifier() {
    return getQualifierExpression();
  }

  public String getReferenceName() {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      if (nameElement.getNode().getElementType() == GroovyElementTypes.mSTRING_LITERAL) {
        return GrStringUtil.removeQuotes(nameElement.getText());
      }
      return nameElement.getText();
    }
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final PsiElement resolved = resolve();
    if (resolved instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) resolved;
      final String oldName = getReferenceName();
      if (!method.getName().equals(oldName)) { //was property reference to accessor
        if (GroovyPropertyUtils.isSimplePropertyAccessor(method)) {
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
      if (oldName != null && !oldName.equals(field.getName())) { //was accessor reference to property
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
      PsiElement element = GroovyPsiElementFactory.getInstance(getProject()).createStringLiteral(newElementName);
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
    ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, RESOLVER, true, false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  private static final OurResolver RESOLVER = new OurResolver();

  private static final OurTypesCalculator TYPES_CALCULATOR = new OurTypesCalculator();

  public GrReferenceExpression getElementToCompare() {
    return this;
  }

  public int compareTo(GrReferenceExpression grReferenceExpression) {
    if (this.equals(grReferenceExpression)) return 0;
    else return getText().compareTo(grReferenceExpression.getText());
  }

  public PsiType getNominalType() {
    return GroovyPsiManager.getInstance(getProject()).getTypeInferenceHelper().doWithInferenceDisabled(new Computable<PsiType>() {
      public PsiType compute() {
        return getNominalTypeImpl();
      }
    });
  }

  @Nullable
  private PsiType getNominalTypeImpl() {
    IElementType dotType = getDotTokenType();

    final GroovyResolveResult resolveResult = advancedResolve();
    PsiElement resolved = resolveResult.getElement();
    if (dotType == GroovyTokenTypes.mMEMBER_POINTER) {
      if (resolved instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) resolved;
        PsiType returnType = resolveResult.getSubstitutor().substitute(method.getReturnType());
        return GrClosureType.create(getResolveScope(), returnType, method.getParameterList().getParameters(), getManager());
      }
      return JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeByFQClassName(GrClosableBlock.GROOVY_LANG_CLOSURE, getResolveScope());
    }
    PsiType result = null;
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    if (resolved == null && !"class".equals(getReferenceName())) {
      resolved = getReference().resolve();
    }
    if (resolved instanceof PsiClass) {
      if (getParent() instanceof GrReferenceExpression) {
        result = facade.getElementFactory().createType((PsiClass) resolved);
      } else {
        PsiClass javaLangClass = facade.findClass("java.lang.Class", getResolveScope());
        if (javaLangClass != null) {
          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
          final PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
          if (typeParameters.length == 1) {
            substitutor = substitutor.put(typeParameters[0], facade.getElementFactory().createType((PsiClass) resolved));
          }
          result = facade.getElementFactory().createType(javaLangClass, substitutor);
        }
      }
    } else if (resolved instanceof GrVariableBase) {
      result = ((GrVariableBase) resolved).getDeclaredType();
    } else if (resolved instanceof PsiVariable) {
      result = ((PsiVariable) resolved).getType();
    } else
    if (resolved instanceof PsiMethod && !GroovyPsiManager.isTypeBeingInferred(resolved)) {
      if (dotType == GroovyTokenTypes.mMEMBER_POINTER) {
        return facade.getElementFactory().createTypeByFQClassName("groovy.lang.Closure", getResolveScope());
      }
      PsiMethod method = (PsiMethod) resolved;
      if (PropertyUtil.isSimplePropertySetter(method) && !method.getName().equals(getReferenceName())) {
        result = method.getParameterList().getParameters()[0].getType();
      } else {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && "java.lang.Object".equals(containingClass.getQualifiedName()) &&
                "getClass".equals(method.getName())) {
          result = getTypeForObjectGetClass(facade, method);
        } else {
          if (method instanceof GrAccessorMethod) {
            result = ((GrAccessorMethod) method).getReturnTypeGroovy();
          } else {
            result = method.getReturnType();
          }
        }

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
        return JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeByFQClassName("java.lang.Class",
                getResolveScope());
      }

      GrExpression qualifier = getQualifierExpression();
      if (qualifier != null) {
        PsiType qType = qualifier.getType();
        if (qType instanceof PsiClassType) {
          PsiClassType.ClassResolveResult qResult = ((PsiClassType) qType).resolveGenerics();
          PsiClass clazz = qResult.getElement();
          if (clazz != null) {
            PsiClass mapClass = facade.findClass("java.util.Map", getResolveScope());
            if (mapClass != null && mapClass.getTypeParameters().length == 2) {
              PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(mapClass, clazz, qResult.getSubstitutor());
              if (substitutor != null) {
                return substitutor.substitute(mapClass.getTypeParameters()[1]);
              }
            }
          }
        }
      }
    }

    if (result != null) {
      result = resolveResult.getSubstitutor().substitute(result);
      result = TypesUtil.boxPrimitiveType(result, getManager(), getResolveScope());
    }
    if (dotType != GroovyTokenTypes.mSPREAD_DOT) {
      return result;
    } else {
      return ResolveUtil.getListTypeForSpreadOperator(this, result);
    }
  }

  @Nullable
  private PsiType getTypeForObjectGetClass(JavaPsiFacade facade, PsiMethod method) {
    PsiType type = method.getReturnType();
    if (type instanceof PsiClassType) {
      PsiClass clazz = ((PsiClassType) type).resolve();
      if (clazz != null &&
              "java.lang.Class".equals(clazz.getQualifiedName())) {
        PsiTypeParameter[] typeParameters = clazz.getTypeParameters();
        if (typeParameters.length == 1) {
          PsiClass qualifierClass = null;
          GrExpression qualifier = getQualifierExpression();
          if (qualifier != null) {
            PsiType qualifierType = qualifier.getType();
            if (qualifierType instanceof PsiClassType) {
              qualifierClass = ((PsiClassType) qualifierType).resolve();
            }
          } else {
            PsiNamedElement context = PsiTreeUtil.getParentOfType(this, PsiClass.class, GroovyFile.class);
            if (context instanceof PsiClass) qualifierClass = (PsiClass) context;
            else if (context instanceof GroovyFile) qualifierClass = ((GroovyFile) context).getScriptClass();
          }

          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
          if (qualifierClass != null) {
            PsiType t = facade.getElementFactory().createType(qualifierClass);
            substitutor = substitutor.put(typeParameters[0], t);
          }
          return facade.getElementFactory().createType(clazz, substitutor);
        }
      }
    }
    return type;
  }

  private static final class OurTypesCalculator implements Function<GrReferenceExpressionImpl, PsiType> {
    public PsiType fun(GrReferenceExpressionImpl refExpr) {
      final PsiType inferred = GroovyPsiManager.getInstance(refExpr.getProject()).getTypeInferenceHelper().getInferredType(refExpr);
      final PsiType nominal = refExpr.getNominalTypeImpl();
      if (inferred == null || PsiType.NULL.equals(inferred)) {
        if (nominal == null) {
          /*inside nested closure we could still try to infer from variable initializer.
          * Not sound, but makes sense*/
          final PsiElement resolved = refExpr.resolve();
          if (resolved instanceof GrVariableBase) return ((GrVariableBase) resolved).getTypeGroovy();
        }

        return nominal;
      }

      if (nominal == null) return inferred;
      if (!TypeConversionUtil.isAssignable(nominal, inferred, false)) {
        final PsiElement resolved = refExpr.resolve();
        if (resolved instanceof GrVariable && ((GrVariable) resolved).getTypeElementGroovy() != null) {
          return nominal; //see GRVY-487
        }
      }
      return inferred;
    }
  }

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPES_CALCULATOR);
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    return PsiImplUtil.replaceExpression(this, newExpr, removeUnnecessaryParentheses);
  }

  public String getName() {
    return getReferenceName();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiElement nameElement = getReferenceNameElement();
    ASTNode node = nameElement.getNode();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(name).getNode();
    assert newNameNode != null && node != null;
    node.getTreeParent().replaceChild(node, newNameNode);

    return this;
  }

  private static class OurResolver implements ResolveCache.PolyVariantResolver<GrReferenceExpressionImpl> {
    public GroovyResolveResult[] resolve(GrReferenceExpressionImpl refExpr, boolean incompleteCode) {
      String name = refExpr.getReferenceName();
      if (name == null) return null;
      ResolverProcessor processor = getMethodOrPropertyResolveProcessor(refExpr, name, incompleteCode);

      resolveImpl(refExpr, processor);

      GroovyResolveResult[] propertyCandidates = processor.getCandidates();
      if (propertyCandidates.length > 0) return propertyCandidates;
      if (refExpr.getKind() == Kind.TYPE_OR_PROPERTY) {
        EnumSet<ClassHint.ResolveKind> kinds = refExpr.getParent() instanceof GrReferenceExpression ?
                EnumSet.of(ClassHint.ResolveKind.CLASS, ClassHint.ResolveKind.PACKAGE) :
                EnumSet.of(ClassHint.ResolveKind.CLASS);
        ResolverProcessor classProcessor = new ClassResolverProcessor(refExpr.getReferenceName(), refExpr, kinds);
        resolveImpl(refExpr, classProcessor);
        return classProcessor.getCandidates();
      }

      return GroovyResolveResult.EMPTY_ARRAY;
    }

    private void resolveImpl(GrReferenceExpressionImpl refExpr, ResolverProcessor processor) {
      GrExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier == null) {
        ResolveUtil.treeWalkUp(refExpr, processor, true);
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
            PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(listClass, clazz, result.getSubstitutor());
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
            if (!resolved.processDeclarations(processor, ResolveState.initial(), null, refExpr)) return;
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
              PsiClass javaLangClass = PsiUtil.getJavaLangClass(resolved, refExpr.getResolveScope());
              if (javaLangClass != null) {
                ResolveState state = ResolveState.initial();
                PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
                PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
                if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
                if (typeParameters.length == 1) {
                  substitutor = substitutor.put(typeParameters[0], qualifierType);
                  state = state.put(PsiSubstitutor.KEY, substitutor);
                }
                if (!javaLangClass.processDeclarations(processor, state, null, refExpr)) return;
                PsiType javaLangClassType = JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory().createType(javaLangClass, substitutor);
                ResolveUtil.processNonCodeMethods(javaLangClassType, processor, refExpr.getProject(), refExpr, false);
              }
            }
          }
        }
      }
    }

    private static void processClassQualifierType(GrReferenceExpressionImpl refExpr, ResolverProcessor processor, PsiType qualifierType) {
      Project project = refExpr.getProject();
      if (qualifierType instanceof PsiClassType) {
        PsiClassType.ClassResolveResult qualifierResult = ((PsiClassType) qualifierType).resolveGenerics();
        PsiClass qualifierClass = qualifierResult.getElement();
        if (qualifierClass != null) {
          if (!qualifierClass.processDeclarations(processor,
                  ResolveState.initial().put(PsiSubstitutor.KEY, qualifierResult.getSubstitutor()), null, refExpr))
            return;
        }
        if (!ResolveUtil.processCategoryMembers(refExpr, processor, (PsiClassType) qualifierType)) return;
      } else if (qualifierType instanceof PsiArrayType) {
        final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(project).getArrayClass();
        if (!arrayClass.processDeclarations(processor, ResolveState.initial(), null, refExpr)) return;
      } else if (qualifierType instanceof PsiIntersectionType) {
        for (PsiType conjunct : ((PsiIntersectionType) qualifierType).getConjuncts()) {
          processClassQualifierType(refExpr, processor, conjunct);
        }
        return;
      }

      ResolveUtil.processNonCodeMethods(qualifierType, processor, project, refExpr, false);
    }
  }

  private static ResolverProcessor getMethodOrPropertyResolveProcessor(GrReferenceExpression refExpr, String name, boolean incomplete) {
    if (incomplete) return CompletionProcessor.createRefSameNameProcessor(refExpr, name);

    Kind kind = ((GrReferenceExpressionImpl) refExpr).getKind();
    ResolverProcessor processor;
    if (kind == Kind.METHOD_OR_PROPERTY) {
      final PsiType[] argTypes = PsiUtil.getArgumentTypes(refExpr, false, false);
      PsiType thisType = getThisType(refExpr);
      processor = new MethodResolverProcessor(name, refExpr, false, thisType, argTypes, refExpr.getTypeArguments());
    } else {
      processor = new PropertyResolverProcessor(name, refExpr);
    }

    return processor;
  }

  private static PsiType getThisType(GrReferenceExpression refExpr) {
    GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier != null) {
      PsiType qType = qualifier.getType();
      if (qType != null) return qType;
    }

    return TypesUtil.getJavaLangObject(refExpr);
  }


  enum Kind {
    TYPE_OR_PROPERTY,
    METHOD_OR_PROPERTY
  }

  Kind getKind() {
    if (getDotTokenType() == GroovyTokenTypes.mMEMBER_POINTER) return Kind.METHOD_OR_PROPERTY;

    PsiElement parent = getParent();
    if (parent instanceof GrMethodCallExpression || parent instanceof GrApplicationStatement) {
      return Kind.METHOD_OR_PROPERTY;
    }

    return Kind.TYPE_OR_PROPERTY;
  }

  public String getCanonicalText() {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod) element)) {
      final PsiElement target = resolve();
      if (element instanceof GrAccessorMethod && getManager().areElementsEquivalent(((GrAccessorMethod)element).getProperty(), target)) {
        return true;
      }

      return getManager().areElementsEquivalent(element, target);
    }

    if (element instanceof GrField && ((GrField) element).isProperty()) {
      final PsiElement target = resolve();
      if (getManager().areElementsEquivalent(element, target)) {
        return true;
      }

      for (final GrAccessorMethod getter : ((GrField)element).getGetters()) {
        if (getManager().areElementsEquivalent(getter, target)) {
          return true;
        }
      }
      return getManager().areElementsEquivalent(((GrField)element).getSetter(), target);
    }

    if (element instanceof PsiNamedElement && Comparing.equal(((PsiNamedElement) element).getName(), getReferenceName())) {
      return getManager().areElementsEquivalent(element, resolve());
    }
    return false;
  }

  @NotNull
  public Object[] getVariants() {
    return CompleteReferenceExpression.getVariants(this);
  }


  public boolean isSoft() {
    return false;
  }

  public GrExpression getQualifierExpression() {
    return findChildByClass(GrExpression.class);
  }

  public boolean isQualified() {
    return getQualifierExpression() != null;
  }

  @Nullable
  public PsiElement getDotToken() {
    return findChildByType(GroovyTokenTypes.DOTS);
  }

  public void replaceDotToken(PsiElement newDot) {
    if (newDot == null) return;
    if (!GroovyTokenTypes.DOTS.contains(newDot.getNode().getElementType())) return;
    final PsiElement oldDot = getDotToken();
    if (oldDot == null) return;

    getNode().replaceChild(oldDot.getNode(), newDot.getNode());
  }

  @Nullable
  public IElementType getDotTokenType() {
    PsiElement dot = getDotToken();
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

  public void setQualifierExpression(GrReferenceExpression newQualifier) {
    final GrExpression oldQualifier = getQualifierExpression();
    final ASTNode node = getNode();
    final PsiElement refNameElement = getReferenceNameElement();
    if (newQualifier == null) {
      if (oldQualifier != null) {
        if (refNameElement != null) {
          node.removeRange(node.getFirstChildNode(), refNameElement.getNode());
        }
      }
    } else {
      if (oldQualifier != null) {
        node.replaceChild(oldQualifier.getNode(), newQualifier.getNode());
      } else {
        if (refNameElement != null) {
          node.addChild(newQualifier.getNode(), refNameElement.getNode());
          node.addLeaf(GroovyTokenTypes.mDOT, ".", refNameElement.getNode());
        }
      }
    }

  }
}