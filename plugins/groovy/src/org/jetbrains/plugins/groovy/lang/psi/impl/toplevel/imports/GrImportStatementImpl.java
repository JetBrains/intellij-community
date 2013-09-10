/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.List;

/**
 * @author ilyas
 */
public class GrImportStatementImpl extends GroovyPsiElementImpl implements GrImportStatement {

  public GrImportStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitImportStatement(this);
  }

  public String toString() {
    return "Import statement";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (PsiTreeUtil.isAncestor(this, place, false)) {
      return true;
    }
    if (isStatic() && lastParent instanceof GrImportStatement) return true;

    if (isOnDemand()) {
      if (!processDeclarationsForMultipleElements(processor, lastParent, place, state)) return false;
    }
    else {
      if (!processDeclarationsForSingleElement(processor, state)) return false;
    }

    return true;
  }

  private boolean processDeclarationsForSingleElement(PsiScopeProcessor processor, ResolveState state) {
    String name = getImportedName();
    if (name == null) return true;

    NameHint nameHint = processor.getHint(NameHint.KEY);

    GrCodeReferenceElement ref = getImportReference();
    if (ref == null) return true;

    if (isStatic()) {
      return processSingleStaticImport(processor, state, name, nameHint, ref);
    }
    if (nameHint == null || name.equals(nameHint.getName(state))) {
      return processSingleClassImport(processor, state, ref);
    }
    return true;
  }

  @Nullable
  private PsiClass resolveQualifier() {
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<PsiClass>() {
      @Nullable
      @Override
      public Result<PsiClass> compute() {
        GrCodeReferenceElement reference = getImportReference();
        GrCodeReferenceElement qualifier = reference == null ? null : reference.getQualifier();
        PsiElement target = qualifier == null ? null : qualifier.resolve();
        PsiClass clazz = target instanceof PsiClass ? (PsiClass)target : null;
        return Result.create(clazz, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, GrImportStatementImpl.this);
      }
    });
  }

  private static List<PsiMember> getAllStaticMembers(final PsiClass clazz) {
    return CachedValuesManager.getCachedValue(clazz, new CachedValueProvider<List<PsiMember>>() {
      @Nullable
      @Override
      public Result<List<PsiMember>> compute() {
        List<PsiMember> result = ContainerUtil.newArrayList();
        for (PsiMethod method : clazz.getAllMethods()) {
          if (method.hasModifierProperty(PsiModifier.STATIC)) {
            result.add(method);
          }
        }
        for (PsiField field : clazz.getAllFields()) {
          if (field.hasModifierProperty(PsiModifier.STATIC)) {
            result.add(field);
          }
        }
        for (PsiClass inner : clazz.getAllInnerClasses()) {
          if (inner.hasModifierProperty(PsiModifier.STATIC)) {
            result.add(inner);
          }
        }
        return Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, clazz);
      }
    });
  }

  private boolean processSingleStaticImport(PsiScopeProcessor processor,
                                            ResolveState state,
                                            String importedName,
                                            NameHint nameHint,
                                            GrCodeReferenceElement ref) {
    PsiClass clazz = resolveQualifier();
    if (clazz == null) return true;

    state = state.put(ResolverProcessor.RESOLVE_CONTEXT, this);
    String hintName = nameHint == null ? null : nameHint.getName(state);

    final String refName = ref.getReferenceName();
    if (hintName == null || importedName.equals(hintName)) {
      final PsiField field = clazz.findFieldByName(refName, true);
      if (field != null && field.hasModifierProperty(PsiModifier.STATIC)) {
        if (!processor.execute(field, state)) return false;
      }

      for (PsiMethod method : clazz.findMethodsByName(refName, true)) {
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
          if (!processor.execute(method, state)) return false;
        }
      }

      final PsiClass innerClass = clazz.findInnerClassByName(refName, true);
      if (innerClass != null && innerClass.hasModifierProperty(PsiModifier.STATIC) && !processor.execute(innerClass, state)) return false;
    }

    String propByGetter = hintName == null ? null : GroovyPropertyUtils.getPropertyNameByGetterName(hintName, true);
    String propBySetter = hintName == null ? null : GroovyPropertyUtils.getPropertyNameBySetterName(hintName);
    for (PsiMember member : getAllStaticMembers(clazz)) {
      if (!(member instanceof PsiMethod)) {
        continue;
      }

      PsiMethod method = (PsiMethod)member;
      if ((nameHint == null || importedName.equals(propByGetter)) && GroovyPropertyUtils.isSimplePropertyGetter(method, refName) ||
          (nameHint == null || importedName.equals(propBySetter)) && GroovyPropertyUtils.isSimplePropertySetter(method, refName)) {
        if (method.hasModifierProperty(PsiModifier.STATIC) && !processor.execute(method, state)) {
          return false;
        }
      }
    }

    return true;
  }

  private boolean processSingleClassImport(PsiScopeProcessor processor, ResolveState state, GrCodeReferenceElement ref) {
    final PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiClass)) {
      return true;
    }

    if (!isAliasedImport() && isFromSamePackage((PsiClass)resolved)) {
      return true; //don't process classes from the same package because such import statements are ignored by compiler
    }

    return processor.execute(resolved, state.put(ResolverProcessor.RESOLVE_CONTEXT, this));
  }

  private boolean isFromSamePackage(PsiClass resolved) {
    final String qualifiedName = resolved.getQualifiedName();
    final String packageName = ((GroovyFile)getContainingFile()).getPackageName();
    final String assumed = packageName + '.' + resolved.getName();
    return !packageName.isEmpty() && assumed.equals(qualifiedName);
  }

  private boolean processDeclarationsForMultipleElements(PsiScopeProcessor processor,
                                                         @Nullable PsiElement lastParent,
                                                         PsiElement place,
                                                         ResolveState state) {
    GrCodeReferenceElement ref = getImportReference();
    if (ref == null) return true;

    if (isStatic()) {
      final PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiClass) {
        state = state.put(ResolverProcessor.RESOLVE_CONTEXT, this);
        final PsiClass clazz = (PsiClass)resolved;
        if (!processAllMembers(processor, clazz, state)) return false;
      }
    }
    else {
      String qName = PsiUtil.getQualifiedReferenceText(ref);
      if (qName != null) {
        PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(qName);
        if (aPackage != null && !((GroovyFile)getContainingFile()).getPackageName().equals(aPackage.getQualifiedName())) {
          state = state.put(ResolverProcessor.RESOLVE_CONTEXT, this);
          if (!aPackage.processDeclarations(processor, state, lastParent, place)) return false;
        }
      }
    }
    return true;
  }

  private static boolean processAllMembers(PsiScopeProcessor processor, PsiClass clazz, ResolveState state) {
    for (PsiMember member : getAllStaticMembers(clazz)) {
      if (!ResolveUtil.processElement(processor, (PsiNamedElement)member, state)) return false;
    }
    return true;
  }

  public GrCodeReferenceElement getImportReference() {
    return (GrCodeReferenceElement)findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  @Nullable
  public String getImportedName() {
    if (isOnDemand()) return null;

    PsiElement aliasNameElement = getAliasNameElement();
    if (aliasNameElement != null) {
      return aliasNameElement.getText();
    }

    GrCodeReferenceElement ref = getImportReference();
    return ref == null ? null : ref.getReferenceName();
  }

  public boolean isStatic() {
    return findChildByType(GroovyTokenTypes.kSTATIC) != null;
  }

  public boolean isAliasedImport() {
    return getAliasNameElement() != null;
  }

  public boolean isOnDemand() {
    return findChildByType(GroovyTokenTypes.mSTAR) != null;
  }

  @NotNull
  public GrModifierList getAnnotationList() {
    return findNotNullChildByClass(GrModifierList.class);
  }

  @Nullable
  @Override
  public PsiClass resolveTargetClass() {
    final GrCodeReferenceElement ref = getImportReference();
    if (ref == null) return null;

    final PsiElement resolved;
    if (!isStatic() || isOnDemand()) {
      resolved = ref.resolve();
    }
    else {
      resolved = resolveQualifier();
    }

    return resolved instanceof PsiClass ? (PsiClass)resolved : null;
  }

  @Nullable
  @Override
  public PsiElement getAliasNameElement() {
    return findChildByType(GroovyTokenTypes.mIDENT);
  }
}
