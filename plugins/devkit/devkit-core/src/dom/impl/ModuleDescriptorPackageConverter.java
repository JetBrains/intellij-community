// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.daemon.quickFix.CreateClassOrPackageFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PackageReferenceSet;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiPackageReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.PsiPackageConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ContentDescriptor;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

/**
 * Resolve {@code package} attribute using containing module's scope based on {@code module-descriptor.xml} reference via {@code name}.
 */
public class ModuleDescriptorPackageConverter extends PsiPackageConverter {

  @Override
  public PsiPackage fromString(@Nullable String s, ConvertContext context) {
    final GlobalSearchScope scope = getResolveScope(context.getInvocationElement());
    if (scope == null) return null;

    return super.fromString(s, context);
  }

  @Override
  public PsiReference @NotNull [] createReferences(GenericDomValue<PsiPackage> genericDomValue,
                                                   PsiElement element,
                                                   ConvertContext context) {
    final String s = genericDomValue.getStringValue();
    if (s == null) return PsiReference.EMPTY_ARRAY;

    final GlobalSearchScope searchScope = getResolveScope(genericDomValue);
    if (searchScope == null) return PsiReference.EMPTY_ARRAY;

    return new PackageReferenceSet(s, element, ElementManipulators.getOffsetInElement(element), searchScope) {
      @Override
      protected @NotNull PsiPackageReference createReference(TextRange range, int index) {
        return new MyCreatePackageFixPsiPackageReference(this, range, index, getResolveScope());
      }
    }.getPsiReferences();
  }

  private static class MyCreatePackageFixPsiPackageReference extends PsiPackageReference implements LocalQuickFixProvider {

    private final GlobalSearchScope myScope;

    MyCreatePackageFixPsiPackageReference(PackageReferenceSet set, TextRange range, int index, GlobalSearchScope searchScope) {
      super(set, range, index);
      myScope = searchScope;
    }

    @Override
    public LocalQuickFix @Nullable [] getQuickFixes() {
      PsiPackage basePackage = null;
      if (myIndex != 0) {
        final ResolveResult resolveResult = ArrayUtil.getFirstElement(getReferenceSet().getReference(myIndex - 1).multiResolve(false));
        if (resolveResult != null) {
          basePackage = ObjectUtils.tryCast(resolveResult.getElement(), PsiPackage.class);
        }
      }

      final String qualifiedName = ElementManipulators.getValueText(getElement());
      final CreateClassOrPackageFix fix =
        CreateClassOrPackageFix.createFix(qualifiedName, myScope, getElement(), basePackage, null, null, null);

      return fix != null ? new CreateClassOrPackageFix[]{fix} : LocalQuickFix.EMPTY_ARRAY;
    }
  }

  @Nullable
  protected GlobalSearchScope getResolveScope(DomElement domElement) {
    final ContentDescriptor.ModuleDescriptor moduleDescriptor =
      domElement.getParentOfType(ContentDescriptor.ModuleDescriptor.class, true);
    assert moduleDescriptor != null;

    final IdeaPlugin ideaPlugin = moduleDescriptor.getName().getValue();
    if (ideaPlugin == null) return null;

    return getScope(ideaPlugin);
  }

  @Nullable
  protected static GlobalSearchScope getScope(IdeaPlugin ideaPlugin) {
    final Module module = ideaPlugin.getModule();
    if (module == null) return null;

    return module.getModuleScope(false);
  }

  /**
   * Resolve {@code idea-plugin@package} attribute using module scope.
   */
  public static class ForIdeaPlugin extends ModuleDescriptorPackageConverter {

    @Override
    protected GlobalSearchScope getResolveScope(DomElement genericDomValue) {
      final IdeaPlugin ideaPlugin = genericDomValue.getParentOfType(IdeaPlugin.class, true);
      assert ideaPlugin != null;

      return getScope(ideaPlugin);
    }
  }
}
