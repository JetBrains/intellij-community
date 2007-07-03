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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import static org.jetbrains.plugins.groovy.lang.psi.impl.types.GrCodeReferenceElementImpl.ReferenceKind.*;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrCodeReferenceElementImpl extends GrReferenceElementImpl implements GrCodeReferenceElement {
  public GrCodeReferenceElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Reference element";
  }

  public GrCodeReferenceElement getQualifier() {
    return (GrCodeReferenceElement) findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  public void setQualifier(@Nullable GrCodeReferenceElement newQualifier) {
    final GrCodeReferenceElement qualifier = getQualifier();
    if(newQualifier == null) {
      if (qualifier == null) return;
      getNode().removeRange(getNode().getFirstChildNode(), getReferenceNameElement().getNode());
    } else {
      if (qualifier == null) {
        final ASTNode refNameNode = getReferenceNameElement().getNode();
        getNode().addChild(newQualifier.getNode(), refNameNode);
        getNode().addLeaf(GroovyTokenTypes.mDOT, ".", refNameNode);
      } else {
        getNode().replaceChild(qualifier.getNode(), newQualifier.getNode());
      }
    }
  }

  public GrTypeElement[] getTypeArguments() {
    return findChildrenByClass(GrTypeElement.class);
  }

  enum ReferenceKind {
    CONSTRUCTOR,
    CLASS,
    CLASS_OR_PACKAGE,
    PACKAGE_FQ,
    CLASS_OR_PACKAGE_FQ,
    STATIC_MEMBER_FQ
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  private ReferenceKind getKind() {
    PsiElement parent = getParent();
    if (parent instanceof GrCodeReferenceElement) {
      ReferenceKind parentKind = ((GrCodeReferenceElementImpl) parent).getKind();
      if (parentKind == CLASS ||
          parentKind == CONSTRUCTOR ||
          parentKind == STATIC_MEMBER_FQ) return CLASS_OR_PACKAGE;
      return parentKind;
    } else if (parent instanceof GrPackageDefinition) {
      return PACKAGE_FQ;
    } else if (parent instanceof GrImportStatement) {
      final GrImportStatement importStatement = (GrImportStatement) parent;
      if (!importStatement.isStatic() || importStatement.isOnDemand()) {
        return CLASS_OR_PACKAGE_FQ;
      }

      return STATIC_MEMBER_FQ;
    } else if (parent instanceof GrNewExpression) {
      return CONSTRUCTOR;
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

  public boolean isReferenceTo(PsiElement element) {
    return getManager().areElementsEquivalent(element, resolve());
  }

  public Object[] getVariants() {
    return getVariantsImpl(getKind());
  }

  private Object[] getVariantsImpl(ReferenceKind kind) {
    PsiManager manager = getManager();
    switch (kind) {
      case STATIC_MEMBER_FQ:
      {
        final GrCodeReferenceElement qualifier = getQualifier();
        if (qualifier != null) {
          final PsiElement resolve = qualifier.resolve();
          if (resolve instanceof PsiClass) {
            final PsiClass clazz = (PsiClass) resolve;
            List<PsiElement> result = new ArrayList<PsiElement>();

            for (PsiField field : clazz.getFields()) {
              if (field.hasModifierProperty(PsiModifier.STATIC)) {
                result.add(field);
              }
            }

            for (PsiMethod method : clazz.getMethods()) {
              if (method.hasModifierProperty(PsiModifier.STATIC)) {
                result.add(method);
              }
            }

            return result.toArray(new PsiElement[result.size()]);
          }
        }
      }
      //fallthrough

      case PACKAGE_FQ:
      case CLASS_OR_PACKAGE_FQ: {
        final String refText = PsiUtil.getQualifiedReferenceText(this);
        final int lastDot = refText.lastIndexOf(".");
        String parentPackageFQName = lastDot > 0 ? refText.substring(0, lastDot) : "";
        final PsiPackage parentPackage = manager.findPackage(parentPackageFQName);
        if (parentPackage != null) {
          final GlobalSearchScope scope = getResolveScope();
          if (kind == PACKAGE_FQ) {
            return parentPackage.getSubPackages(scope);
          } else {
            final PsiPackage[] subpackages = parentPackage.getSubPackages(scope);
            final PsiClass[] classes = parentPackage.getClasses(scope);
            PsiElement[] result = new PsiElement[subpackages.length + classes.length];
            System.arraycopy(subpackages, 0, result, 0, subpackages.length);
            System.arraycopy(classes, 0, result, subpackages.length, classes.length);
            return result;
          }
        }
      }

      case CONSTRUCTOR: {
        final Object[] classVariants = getVariantsImpl(CLASS);
        List<Object> result = new ArrayList<Object>();
        for (Object variant : classVariants) {
          if (variant instanceof PsiClass) {
            final PsiClass clazz = (PsiClass) variant;
            final LookupElement<PsiClass> lookupElement = LookupElementFactory.getInstance().createLookupElement(clazz);
            GroovyCompletionUtil.setTailTypeForConstructor(clazz, lookupElement);
            result.add(lookupElement);
          }
          else if (variant instanceof LookupElement) {
            final LookupElement lookupElement = (LookupElement) variant;
            final Object obj = lookupElement.getObject();
            if (obj instanceof PsiClass) {
              GroovyCompletionUtil.setTailTypeForConstructor((PsiClass) obj, lookupElement);
            }
            result.add(lookupElement);
          }
          else {
            result.add(variant);
          }
        }
        return result.toArray(new Object[result.size()]);
      }

      case CLASS: {
        GrCodeReferenceElement qualifier = getQualifier();
        if (qualifier != null) {
          PsiElement qualifierResolved = qualifier.resolve();
          if (qualifierResolved instanceof PsiPackage) {
            return ((PsiPackage) qualifierResolved).getClasses();
          } else if (qualifierResolved instanceof PsiClass) {
            return ((PsiClass) qualifierResolved).getInnerClasses();
          }
        } else {
          ResolverProcessor processor = new ResolverProcessor(null, EnumSet.of(ClassHint.ResolveKind.CLASS_OR_PACKAGE), this, true);
          ResolveUtil.treeWalkUp(this, processor);
          return GroovyCompletionUtil.getCompletionVariants(processor.getCandidates());
        }
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }

  private static class MyResolver implements ResolveCache.PolyVariantResolver<GrCodeReferenceElementImpl> {

    public GroovyResolveResult[] resolve(GrCodeReferenceElementImpl reference, boolean incompleteCode) {
      if (reference.getReferenceName() == null) return null;
      return _resolve(reference, reference.getManager(), reference.getKind());
    }

    private GroovyResolveResult[] _resolve(GrCodeReferenceElementImpl ref, PsiManager manager, ReferenceKind kind) {
      final String refName=ref.getReferenceName();
      switch (kind) {
        case CLASS_OR_PACKAGE_FQ: {
          PsiClass aClass = manager.findClass(PsiUtil.getQualifiedReferenceText(ref), ref.getResolveScope());
          if (aClass != null) {
            boolean isAccessible = com.intellij.psi.util.PsiUtil.isAccessible(aClass, ref, null);
            return new GroovyResolveResult[]{new GroovyResolveResultImpl(aClass, isAccessible)};
          }
          //fallthrough
        }

        case PACKAGE_FQ:
          PsiPackage aPackage = manager.findPackage(PsiUtil.getQualifiedReferenceText(ref));
          return new GroovyResolveResult[]{new GroovyResolveResultImpl(aPackage, true)};

        case CLASS:
        case CLASS_OR_PACKAGE: {
          GrCodeReferenceElement qualifier = ref.getQualifier();
          if (qualifier != null) {
            PsiElement qualifierResolved = qualifier.resolve();
            if (qualifierResolved instanceof PsiPackage) {
              PsiClass[] classes = ((PsiPackage) qualifierResolved).getClasses();
              for (final PsiClass aClass : classes) {
                if (refName.equals(aClass.getName())) {
                  boolean isAccessible = com.intellij.psi.util.PsiUtil.isAccessible(aClass, ref, null);
                  return new GroovyResolveResult[]{new GroovyResolveResultImpl(aClass, isAccessible)};
                }
              }

              if (kind == CLASS_OR_PACKAGE) {
                for (final PsiPackage subpackage : ((PsiPackage) qualifierResolved).getSubPackages()) {
                  if (refName.equals(subpackage.getName()))
                    return new GroovyResolveResult[]{new GroovyResolveResultImpl(subpackage, true)};
                }
              }
            }
          } else {
            ResolverProcessor processor = new ResolverProcessor(refName, EnumSet.of(ClassHint.ResolveKind.CLASS_OR_PACKAGE), ref, false);
            ResolveUtil.treeWalkUp(ref, processor);
            GroovyResolveResult[] candidates = processor.getCandidates();
            if (candidates.length > 0) return candidates;

            if (kind == CLASS_OR_PACKAGE) {
              PsiPackage defaultPackage = ref.getManager().findPackage("");
              if (defaultPackage != null) {
                for (final PsiPackage subpackage : defaultPackage.getSubPackages()) {
                  if (refName.equals(subpackage.getName()))
                    return new GroovyResolveResult[]{new GroovyResolveResultImpl(subpackage, true)};
                }
              }
            }
          }

          break;
        }
        case CONSTRUCTOR:
          final GroovyResolveResult[] classResults = _resolve(ref, manager, CLASS);
          if (classResults.length == 0) return GroovyResolveResult.EMPTY_ARRAY;

          final MethodResolverProcessor processor = new MethodResolverProcessor(refName, ref, false, true);
          for (GroovyResolveResult classResult : classResults) {
            final PsiElement element = classResult.getElement();
            if (element instanceof PsiClass) {
              if (!element.processDeclarations(processor, PsiSubstitutor.EMPTY, null, ref)) break;
            }
          }

          final GroovyResolveResult[] constructorResults = processor.getCandidates();
          return constructorResults.length > 0 ? constructorResults : classResults;

        case STATIC_MEMBER_FQ:
        {
          final GrCodeReferenceElement qualifier = ref.getQualifier();
          if (qualifier != null) {
            final PsiElement resolve = qualifier.resolve();
            if (resolve instanceof PsiClass) {
              final PsiClass clazz = (PsiClass) resolve;
              PsiResolveHelper helper = clazz.getManager().getResolveHelper();
              List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
              final PsiField field = clazz.findFieldByName(refName, false);
              if (field != null && field.hasModifierProperty(PsiModifier.STATIC)) {
                result.add(new GroovyResolveResultImpl(field, helper.isAccessible(field, ref, null)));
              }

              final PsiMethod[] methods = clazz.findMethodsByName(refName, false);
              for (PsiMethod method : methods) {
                result.add(new GroovyResolveResultImpl(method, helper.isAccessible(method, ref, null)));
              }

              return result.toArray(new GroovyResolveResult[result.size()]);
            }
          }
        }
      }

      return GroovyResolveResult.EMPTY_ARRAY;
    }
  }

  private static MyResolver RESOLVER = new MyResolver();

  public GroovyResolveResult advancedResolve() {
    ResolveResult[] results = getManager().getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public GroovyResolveResult[] multiResolve(boolean b) {
    return (GroovyResolveResult[]) getManager().getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
  }
}
