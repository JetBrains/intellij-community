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

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeOrPackageReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import static org.jetbrains.plugins.groovy.lang.psi.impl.types.GrTypeOrPackageReferenceElementImpl.ReferenceKind.*;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.EnumSet;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrTypeOrPackageReferenceElementImpl extends GrReferenceElementImpl implements GrTypeOrPackageReferenceElement {
  public GrTypeOrPackageReferenceElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Reference element";
  }

  public GrTypeOrPackageReferenceElement getQualifier() {
    return (GrTypeOrPackageReferenceElement) findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  enum ReferenceKind {
    CONSTRUCTOR,
    CLASS,
    CLASS_OR_PACKAGE,
    PACKAGE_FQ,
    CLASS_OR_PACKAGE_FQ
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] results = ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  private ReferenceKind getKind() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeOrPackageReferenceElement) {
      ReferenceKind parentKind = ((GrTypeOrPackageReferenceElementImpl) parent).getKind();
      if (parentKind == CLASS) return CLASS_OR_PACKAGE;
      return parentKind;
    } else if (parent instanceof GrPackageDefinition) {
      return PACKAGE_FQ;
    } else if (parent instanceof GrImportStatement) {
      return CLASS_OR_PACKAGE_FQ;
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
    PsiManager manager = getManager();
    final ReferenceKind kind = getKind();
    switch (kind) {
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

      case CONSTRUCTOR: //todo
      case CLASS: {
        GrTypeOrPackageReferenceElement qualifier = getQualifier();
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
          GroovyResolveResult[] candidates = processor.getCandidates();
          return ResolveUtil.mapToElements(candidates);
        }
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }

  private static class MyResolver implements ResolveCache.PolyVariantResolver<GrTypeOrPackageReferenceElementImpl> {

    public GroovyResolveResult[] resolve(GrTypeOrPackageReferenceElementImpl reference, boolean incompleteCode) {
      if (reference.getReferenceName() == null) return null;
      return _resolve(reference, reference.getManager(), reference.getKind());
    }

    private GroovyResolveResult[] _resolve(GrTypeOrPackageReferenceElementImpl ref, PsiManager manager, ReferenceKind kind) {
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
        case CLASS_OR_PACKAGE:
          GrTypeOrPackageReferenceElement qualifier = ref.getQualifier();
          String refName = ref.getReferenceName();
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
                  if (refName.equals(subpackage.getName())) return new GroovyResolveResult[]{new GroovyResolveResultImpl(subpackage, true)};
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
                  if (refName.equals(subpackage.getName())) return new GroovyResolveResult[]{new GroovyResolveResultImpl(subpackage, true)};
                }
              }
            }
          }

          break;

        case CONSTRUCTOR:
          final GroovyResolveResult[] classResults = _resolve(ref, manager, CLASS);
          if (classResults.length == 0) return GroovyResolveResult.EMPTY_ARRAY;

          final MethodResolverProcessor processor = new MethodResolverProcessor(ref.getReferenceName(), ref, false, true);
          for (GroovyResolveResult classResult : classResults) {
            final PsiElement element = classResult.getElement();
            if (element instanceof PsiClass) {
              if (!element.processDeclarations(processor, PsiSubstitutor.EMPTY, null, ref)) break;
            }
          }

          return processor.getCandidates();
      }

      return null;
    }
  }

  private static MyResolver RESOLVER = new MyResolver();

  public GroovyResolveResult advancedResolve() {
    ResolveResult[] results = ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean b) {
    return ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
  }
}
