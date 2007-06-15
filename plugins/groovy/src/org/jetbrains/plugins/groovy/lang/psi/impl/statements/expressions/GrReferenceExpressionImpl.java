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
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
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
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.ResolveKind;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author ilyas
 */
public class GrReferenceExpressionImpl extends GrReferenceElementImpl implements GrReferenceExpression {
  public GrReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public PsiElement getReferenceNameElement() {
    PsiElement superNameElement = super.getReferenceNameElement();
    return superNameElement == null ? findChildByType(GroovyTokenTypes.kCLASS) : superNameElement;
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
    ResolveResult[] results = ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  private static final MyResolver RESOLVER = new MyResolver();

  private static final MyTypesCalculator TYPES_CALCULATOR = new MyTypesCalculator();

  private static final class MyTypesCalculator implements Function<GrReferenceExpressionImpl, PsiType> {

    public PsiType fun(GrReferenceExpressionImpl refExpr) {
      IElementType dotType = refExpr.getDotTokenType();
      PsiElement resolved = refExpr.resolve();
      PsiType result = null;
      PsiManager manager = refExpr.getManager();
      if (resolved instanceof PsiClass) {
        result = manager.getElementFactory().createType((PsiClass) resolved);
      } else if (resolved instanceof GrVariable) {
        result = ((GrVariable) resolved).getTypeGroovy();
      } else if (resolved instanceof PsiVariable) {
        result = ((PsiVariable) resolved).getType();
      } else if (resolved instanceof PsiMethod && resolved.getCopyableUserData(ResolveUtil.IS_BEING_RESOLVED) == null) {
        if (dotType == GroovyTokenTypes.mMEMBER_POINTER) {
          return manager.getElementFactory().createTypeByFQClassName("groovy.lang.Closure", refExpr.getResolveScope());
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
        if ("class".equals(refExpr.getReferenceName())) {
          return refExpr.getManager().getElementFactory().createTypeByFQClassName("java.lang.Class",
              refExpr.getResolveScope());
        }
      }

      result = TypesUtil.boxPrimitiveType(result, manager, refExpr.getResolveScope());
      if (dotType != GroovyTokenTypes.mSPREAD_DOT) {
        return result;
      } else {
        return ResolveUtil.getListTypeForSpreadOperator(refExpr, result);
      }
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
    throw new IncorrectOperationException("Not implemented");
  }

  private static class MyResolver implements ResolveCache.PolyVariantResolver<GrReferenceExpressionImpl> {
    public GroovyResolveResult[] resolve(GrReferenceExpressionImpl refExpr, boolean incompleteCode) {
      String name = refExpr.getReferenceName();
      if (name == null) return null;
      ResolverProcessor processor = getMethodOrPropertyResolveProcessor(refExpr, name, false);

      resolveImpl(refExpr, processor);

      GroovyResolveResult[] propertyCandidates = processor.getCandidates();
      if (propertyCandidates.length > 0) return propertyCandidates;
      if (refExpr.getKind() == Kind.TYPE_OR_PROPERTY) {
        ResolverProcessor classProcessor = new ResolverProcessor(refExpr.getReferenceName(), EnumSet.of(ResolveKind.CLASS_OR_PACKAGE), refExpr, false);
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
          qualifier = getRuntimeQualifier(refExpr);
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
              PsiClass javaLangClass = getJavaLangObject(resolved, refExpr.getResolveScope());
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
        PsiClass qualifierClass = ((PsiClassType) qualifierType).resolve();
        if (qualifierClass != null) {
          if (!qualifierClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, refExpr)) return;
        }
      } else if (qualifierType instanceof PsiArrayType) {
        final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(project).getArrayClass();
        if (!arrayClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, refExpr)) return;
      }

      ResolveUtil.processDefaultMethods(qualifierType, processor, project);
    }
  }

  private static GrExpression getRuntimeQualifier(GrReferenceExpressionImpl refExpr) {
    GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) {
      GrClosableBlock closure = PsiTreeUtil.getParentOfType(refExpr, GrClosableBlock.class);
      while (closure != null) {
        GrExpression funExpr = null;
        PsiElement parent = closure.getParent();
        if (parent instanceof GrApplicationExpression) {
          funExpr = ((GrApplicationExpression) parent).getFunExpression();
        } else if (parent instanceof GrMethodCall) {
          funExpr = ((GrMethodCall) parent).getInvokedExpression();
        }
        if (funExpr instanceof GrReferenceExpression) {
          qualifier = ((GrReferenceExpression) funExpr).getQualifierExpression();
          if (qualifier != null) break;
        } else break;

        closure = PsiTreeUtil.getParentOfType(closure, GrClosableBlock.class);
      }
    }

    return qualifier;
  }

  private static ResolverProcessor getMethodOrPropertyResolveProcessor(GrReferenceExpressionImpl refExpr, String name, boolean forCompletion) {
    Kind kind = refExpr.getKind();
    ResolverProcessor processor;
    if (kind == Kind.METHOD_OR_PROPERTY) {
      processor = new MethodResolverProcessor(name, refExpr, forCompletion);
    } else {
      processor = new PropertyResolverProcessor(name, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), refExpr, forCompletion);
    }

    return processor;
  }

  private enum Kind {
    PROPERTY,
    TYPE_OR_PROPERTY,
    METHOD_OR_PROPERTY
  }

  private Kind getKind() {
    PsiElement parent = getParent();
    if (parent instanceof GrMethodCall || parent instanceof GrApplicationExpression) {
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
    if (element instanceof PsiNamedElement && Comparing.equal(((PsiNamedElement) element).getName(), getReferenceName())) {
      return element.equals(resolve());
    }
    return false;
  }

  public Object[] getVariants() {

    Object[] propertyVariants = getVariantsImpl(getMethodOrPropertyResolveProcessor(this, null, true));
    PsiElement parent = getParent();
    if (parent instanceof GrArgumentList) {
      GrExpression call = (GrExpression) parent.getParent(); //add named argument label variants
      PsiType type = call.getType();
      if (type instanceof PsiClassType) {
        PsiClass clazz = ((PsiClassType) type).resolve();
        if (clazz != null) {
          List<String> props = new ArrayList<String>();
          for (PsiMethod method : clazz.getAllMethods()) {
            if (PropertyUtil.isSimplePropertySetter(method)) {
              String prop = PropertyUtil.getPropertyName(method);
              if (prop != null) {
                props.add(prop);
              }
            }
          }

          if (props.size() > 0) {
            propertyVariants = ArrayUtil.mergeArrays(propertyVariants, props.toArray(new Object[props.size()]), Object.class);
          }

          propertyVariants = ArrayUtil.mergeArrays(propertyVariants, clazz.getFields(), Object.class);
        }
      }
    }


    if (getKind() == Kind.TYPE_OR_PROPERTY) {
      ResolverProcessor classVariantsCollector = new ResolverProcessor(null, EnumSet.of(ResolveKind.CLASS_OR_PACKAGE), this, true);
      getVariantsImpl(classVariantsCollector);
      GroovyResolveResult[] classVariants = classVariantsCollector.getCandidates();
      return ArrayUtil.mergeArrays(propertyVariants, ResolveUtil.mapToElements(classVariants), Object.class);
    }


    return propertyVariants;
  }

  private Object[] getVariantsImpl(ResolverProcessor processor) {
    GrExpression qualifier = getQualifierExpression();
    if (qualifier == null) {
      ResolveUtil.treeWalkUp(this, processor);
      qualifier = getRuntimeQualifier(this);
      if (qualifier != null) getVariantsFromQualifier(processor, qualifier);
    } else {
      if (getDotTokenType() != GroovyTokenTypes.mSPREAD_DOT) {
        getVariantsFromQualifier(processor, qualifier);
      } else {
        getVariantsFromQualifierForSpreadOperator(processor, qualifier);
      }
    }

    GroovyResolveResult[] candidates = processor.getCandidates();
    if (candidates.length == 0) return PsiNamedElement.EMPTY_ARRAY;
    PsiElement[] elements = ResolveUtil.mapToElements(candidates);
    String[] properties = addPretendedProperties(elements);
    return ArrayUtil.mergeArrays(elements, properties, Object.class);
  }

  private void getVariantsFromQualifierForSpreadOperator(ResolverProcessor processor, GrExpression qualifier) {
    PsiType qualifierType = qualifier.getType();
    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType) qualifierType).resolveGenerics();
      PsiClass clazz = result.getElement();
      if (clazz != null) {
        PsiClass listClass = getManager().findClass("java.util.List", getResolveScope());
        if (listClass != null && listClass.getTypeParameters().length == 1) {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(clazz, listClass, result.getSubstitutor());
          if (substitutor != null) {
            PsiType componentType = substitutor.substitute(listClass.getTypeParameters()[0]);
            if (componentType != null) {
              getVaiantsFromQualifierType(processor, componentType, getProject());
            }
          }
        }
      }
    } else if (qualifierType instanceof PsiArrayType) {
      getVaiantsFromQualifierType(processor, ((PsiArrayType) qualifierType).getComponentType(), getProject());
    }
  }

  private String[] addPretendedProperties(PsiElement[] elements) {
    List<String> result = new ArrayList<String>();
    for (PsiElement element : elements) {
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) element;
        String propName = PropertyUtil.getPropertyName(method);
        if (propName != null) {
          result.add(propName);
        }
      }
    }

    return result.toArray(new String[result.size()]);
  }


  private void getVariantsFromQualifier(ResolverProcessor processor, GrExpression qualifier) {
    PsiType qualifierType = qualifier.getType();
    if (qualifierType == null) {
      if (qualifier instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression) qualifier).resolve();
        if (resolved instanceof PsiPackage) {
          resolved.processDeclarations(processor, PsiSubstitutor.EMPTY, null, this);
        }
      }
    } else {
      Project project = qualifier.getProject();
      if (qualifierType instanceof PsiIntersectionType) {
        for (PsiType conjunct : ((PsiIntersectionType) qualifierType).getConjuncts()) {
          getVaiantsFromQualifierType(processor, conjunct, project);
        }
      } else {
        getVaiantsFromQualifierType(processor, qualifierType, project);
        if (qualifier instanceof GrReferenceExpression) {
          PsiElement resolved = ((GrReferenceExpression) qualifier).resolve();
          if (resolved instanceof PsiClass) { ////omitted .class
            GlobalSearchScope scope = getResolveScope();
            PsiClass javaLangClass = getJavaLangObject(resolved, scope);
            if (javaLangClass != null) {
              javaLangClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, this);
            }
          }
        }
      }
    }
  }

  private static PsiClass getJavaLangObject(PsiElement resolved, GlobalSearchScope scope) {
    return resolved.getManager().findClass("java.lang.Class", scope);
  }

  private void getVaiantsFromQualifierType(ResolverProcessor processor, PsiType qualifierType, Project project) {
    if (qualifierType instanceof PsiClassType) {
      PsiClass qualifierClass = ((PsiClassType) qualifierType).resolve();
      if (qualifierClass != null) {
        qualifierClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, this);
      }
    } else if (qualifierType instanceof PsiArrayType) {
      final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(project).getArrayClass();
      if (!arrayClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, this)) return;
    }

    ResolveUtil.processDefaultMethods(qualifierType, processor, project);
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
    ResolveResult[] results = ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean b) {
    return ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
  }

}