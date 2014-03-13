/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrImportStatementStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import static org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil.shouldProcessMethods;

/**
 * @author ilyas
 */
public class GrImportStatementImpl extends GrStubElementBase<GrImportStatementStub> implements GrImportStatement, StubBasedPsiElement<GrImportStatementStub> {

  public GrImportStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrImportStatementImpl(GrImportStatementStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }


  @Override
  public PsiElement getParent() {
    return getParentByStub();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitImportStatement(this);
  }

  public String toString() {
    return "Import statement";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (isAncestor(place)) {
      return true;
    }
    if (isStatic() && lastParent instanceof GrImportStatement) return true;

    if (isOnDemand()) {
      if (!processDeclarationsForMultipleElements(processor, lastParent, place, state)) return false;
    }
    else {
      if (!processDeclarationsForSingleElement(processor, lastParent, place, state)) return false;
    }

    return true;
  }

  private boolean isAncestor(@Nullable PsiElement place) {
    while (place instanceof GrCodeReferenceElement) {
      PsiElement parent = place.getParent();
      if (parent == this) return true;
      place = parent;
    }
    return false;
  }

  private boolean processDeclarationsForSingleElement(@NotNull PsiScopeProcessor processor,
                                                      @Nullable PsiElement lastParent,
                                                      @NotNull PsiElement place,
                                                      @NotNull ResolveState state) {
    String name = getImportedName();
    if (name == null) return true;

    if (isStatic()) {
      return processSingleStaticImport(processor, state, name, lastParent, place);
    }

    NameHint nameHint = processor.getHint(NameHint.KEY);
    if (nameHint == null || name.equals(nameHint.getName(state))) {
      return processSingleClassImport(processor, state);
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

  private boolean processSingleStaticImport(@NotNull final PsiScopeProcessor processor,
                                            @NotNull ResolveState state,
                                            @NotNull String importedName,
                                            @Nullable PsiElement lastParent,
                                            @NotNull PsiElement place) {
    final GrCodeReferenceElement ref = getImportReference();
    if (ref == null) return true;

    PsiClass clazz = resolveQualifier();
    if (clazz == null) return true;

    state = state.put(ResolverProcessor.RESOLVE_CONTEXT, this);

    final String refName = ref.getReferenceName();

    final NameHint nameHint = processor.getHint(NameHint.KEY);
    final String hintName = nameHint == null ? null : nameHint.getName(state);

    if (hintName == null || importedName.equals(hintName)) {
      if (!clazz.processDeclarations(new DelegatingScopeProcessorWithName(processor, refName), state, lastParent, place)) {
        return false;
      }
    }

    ClassHint classHint = state.get(ClassHint.KEY);

    if (shouldProcessMethods(classHint)) {
      if (hintName == null || importedName.equals(GroovyPropertyUtils.getPropertyNameByGetterName(hintName, true))) {
        if (!clazz.processDeclarations(new StaticGetterProcessor(place, refName, processor), state, lastParent, place)) {
          return false;
        }
      }

      if (hintName == null || importedName.equals(GroovyPropertyUtils.getPropertyNameBySetterName(hintName))) {
        if (!clazz.processDeclarations(new StaticSetterProcessor(place, refName, processor), state, lastParent, place)) {
          return false;
        }
      }
    }

    return true;
  }

  private boolean processSingleClassImport(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state) {
    GrCodeReferenceElement ref = getImportReference();
    if (ref == null) return true;

    final PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiClass)) {
      return true;
    }

    if (!isAliasedImport() && isFromSamePackage((PsiClass)resolved)) {
      return true; //don't process classes from the same package because such import statements are ignored by compiler
    }

    return processor.execute(resolved, state.put(ResolverProcessor.RESOLVE_CONTEXT, this));
  }

  private boolean isFromSamePackage(@NotNull PsiClass resolved) {
    final String qualifiedName = resolved.getQualifiedName();
    final String packageName = ((GroovyFile)getContainingFile()).getPackageName();
    final String assumed = packageName + '.' + resolved.getName();
    return !packageName.isEmpty() && assumed.equals(qualifiedName);
  }

  private boolean processDeclarationsForMultipleElements(@NotNull final PsiScopeProcessor processor,
                                                         @Nullable PsiElement lastParent,
                                                         @NotNull PsiElement place,
                                                         @NotNull ResolveState state) {
    GrCodeReferenceElement ref = getImportReference();
    if (ref == null) return true;

    if (isStatic()) {
      final PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiClass) {
        state = state.put(ResolverProcessor.RESOLVE_CONTEXT, this);
        final PsiClass clazz = (PsiClass)resolved;
        if (!clazz.processDeclarations(new DelegatingScopeProcessor(processor) {
          @Override
          public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
            if (element instanceof PsiMember && ((PsiMember)element).hasModifierProperty(PsiModifier.STATIC)) {
              return super.execute(element, state);
            }
            return true;
          }
        }, state, lastParent, place)) return false;
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

  @Override
  public GrCodeReferenceElement getImportReference() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      String referenceText = stub.getReferenceText();
      if (referenceText == null) {
        return null;
      }

      return GroovyPsiElementFactory.getInstance(getProject()).createCodeReferenceElementFromText(referenceText);
    }

    return (GrCodeReferenceElement)findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  @Override
  @Nullable
  public String getImportedName() {
    if (isOnDemand()) return null;

    GrImportStatementStub stub = getStub();
    if (stub != null) {
      String name = stub.getAliasName();
      if (name != null) {
        return name;
      }

      String referenceText = stub.getReferenceText();
      if (referenceText == null) return null;

      return StringUtil.getShortName(referenceText);
    }


    PsiElement aliasNameElement = getAliasNameElement();
    if (aliasNameElement != null) {
      return aliasNameElement.getText();
    }

    GrCodeReferenceElement ref = getImportReference();
    return ref == null ? null : ref.getReferenceName();
  }

  @Override
  public boolean isStatic() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.isStatic();
    }

    return findChildByType(GroovyTokenTypes.kSTATIC) != null;
  }

  @Override
  public boolean isAliasedImport() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getAliasName() != null;
    }
    return getAliasNameElement() != null;
  }

  @Override
  public boolean isOnDemand() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.isOnDemand();
    }
    return findChildByType(GroovyTokenTypes.mSTAR) != null;
  }

  @Override
  @NotNull
  public GrModifierList getAnnotationList() {
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      return ObjectUtils.assertNotNull(getStubOrPsiChild(GroovyElementTypes.MODIFIERS));
    }
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
    GrImportStatementStub stub = getStub();
    if (stub != null) {
      String alias = stub.getAliasName();
      if (alias == null) return null;

      GrImportStatement imp = GroovyPsiElementFactory.getInstance(getProject()).createImportStatementFromText("import A as " + alias);
      return imp.getAliasNameElement();
    }

    return findChildByType(GroovyTokenTypes.mIDENT);
  }

  private static class DelegatingScopeProcessorWithName extends DelegatingScopeProcessor {
    private final NameHint myNameHint;

    public DelegatingScopeProcessorWithName(PsiScopeProcessor processor, final String refName) {
      super(processor);
      myNameHint = new NameHint() {
        @Nullable
        @Override
        public String getName(@NotNull ResolveState state) {
          return refName;
        }
      };
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if (element instanceof PsiMember && ((PsiMember)element).hasModifierProperty(PsiModifier.STATIC)) {
        return super.execute(element, state);
      }
      return true;
    }

    @Nullable
    @Override
    public <T> T getHint(@NotNull Key<T> hintKey) {
      if (hintKey == NameHint.KEY) {
        return (T)myNameHint;
      }
      return super.getHint(hintKey);
    }
  }

  private static class StaticSetterProcessor extends StaticAccessorProcessor {

    public StaticSetterProcessor(PsiElement place, String refName, PsiScopeProcessor processor) {
      super(place, refName, processor);
    }

    protected boolean isAccessor(@NotNull PsiMethod method) {
      return GroovyPropertyUtils.isSimplePropertySetter(method, myRefName);
    }
  }

  private static class StaticGetterProcessor extends StaticAccessorProcessor {

    public StaticGetterProcessor(PsiElement place, String refName, PsiScopeProcessor processor) {
      super(place, refName, processor);
    }

    @Override
    protected boolean isAccessor(@NotNull PsiMethod method) {
      return GroovyPropertyUtils.isSimplePropertyGetter(method, myRefName);
    }
  }

  /**
   * Created by Max Medvedev on 26/03/14
   */
  private static abstract class StaticAccessorProcessor extends ResolverProcessor {
    protected final String myRefName;
    protected final PsiScopeProcessor myProcessor;

    public StaticAccessorProcessor(@NotNull PsiElement place,
                                   @NotNull String refName,
                                   @NotNull PsiScopeProcessor processor) {
      super(null, ResolverProcessor.RESOLVE_KINDS_METHOD, place, PsiType.EMPTY_ARRAY);
      myRefName = refName;
      myProcessor = processor;
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        if (method.hasModifierProperty(PsiModifier.STATIC) && isAccessor(method)) {
          return myProcessor.execute(method, state);
        }
      }

      return true;
    }

    protected abstract boolean isAccessor(@NotNull PsiMethod method);
  }
}
