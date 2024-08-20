// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.patterns.uast.UastPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.UastUtils;

import java.util.Collection;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.*;
import static org.jetbrains.idea.devkit.references.IconsReferencesQueryExecutor.*;

final class IconsUastReferencesContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registerForPresentationAnnotation(registrar);
    registerForIconLoaderMethods(registrar);
  }

  private static void registerForIconLoaderMethods(@NotNull PsiReferenceRegistrar registrar) {
    PsiMethodPattern method = psiMethod().withName("load").definedInClass(ALL_ICONS_FQN);
    PsiJavaElementPattern.Capture<PsiLiteralExpression> findGetIconPattern
      = literalExpression().and(psiExpression().methodCallParameter(0, method));
    registrar.registerReferenceProvider(findGetIconPattern, new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (!IntelliJProjectUtil.isIntelliJPlatformProject(element.getProject())) return PsiReference.EMPTY_ARRAY;
        return new FileReferenceSet(element) {
          @Override
          public @NotNull Collection<PsiFileSystemItem> getDefaultContexts() {
            ModuleManager moduleManager = ModuleManager.getInstance(element.getProject());
            Module iconsModule = moduleManager.findModuleByName(PLATFORM_ICONS_MODULE);
            if (iconsModule == null) {
              iconsModule = moduleManager.findModuleByName(ICONS_MODULE);
            }
            if (iconsModule == null) {
              return super.getDefaultContexts();
            }

            List<PsiFileSystemItem> result = new SmartList<>();
            VirtualFile[] roots = ModuleRootManager.getInstance(iconsModule).getSourceRoots();
            PsiManager psiManager = element.getManager();
            for (VirtualFile root : roots) {
              PsiDirectory directory = psiManager.findDirectory(root);
              ContainerUtil.addIfNotNull(result, directory);
            }
            return result;
          }
        }.getAllReferences();
      }
    }, PsiReferenceRegistrar.HIGHER_PRIORITY);
  }

  private static void registerForPresentationAnnotation(@NotNull PsiReferenceRegistrar registrar) {
    UastReferenceRegistrar.registerUastReferenceProvider(
      registrar,
      UastPatterns.injectionHostUExpression()
        .sourcePsiFilter(psi -> PsiUtil.isPluginProject(psi.getProject()))
        .annotationParam(Presentation.class.getName(), "icon"),
      UastReferenceRegistrar.uastInjectionHostReferenceProvider((uElement, referencePsiElement) -> new PsiReference[]{
        new IconPsiReferenceBase(referencePsiElement) {
          @Override
          public PsiElement resolve() {
            String value = UastUtils.evaluateString(uElement);
            return resolveIconPath(value, referencePsiElement);
          }

          @Override
          public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
            PsiElement field = resolve();
            PsiElement result = handleElement(field, newElementName);
            if (result != null) {
              return result;
            }
            return super.handleElementRename(newElementName);
          }

          private @Nullable PsiElement handleElement(PsiElement element, @Nullable String newElementName) {
            if (element instanceof PsiField) {
              PsiClass containingClass = ((PsiField)element).getContainingClass();
              if (containingClass != null) {
                String classQualifiedName = containingClass.getQualifiedName();
                if (classQualifiedName != null) {
                  if (newElementName == null) {
                    newElementName = ((PsiField)element).getName();
                  }
                  if (classQualifiedName.startsWith(COM_INTELLIJ_ICONS_PREFIX)) {
                    return replace(newElementName, classQualifiedName, COM_INTELLIJ_ICONS_PREFIX);
                  }
                  if (classQualifiedName.startsWith(ICONS_PACKAGE_PREFIX)) {
                    return replace(newElementName, classQualifiedName, ICONS_PACKAGE_PREFIX);
                  }
                  return ElementManipulators.handleContentChange(myElement, classQualifiedName + "." + newElementName);
                }
              }
            }
            return null;
          }

          private PsiElement replace(@NonNls String newElementName, @NonNls String fqn, @NonNls String packageName) {
            String newValue = fqn.substring(packageName.length()) + "." + newElementName;
            return ElementManipulators.handleContentChange(getElement(), newValue);
          }
        }
      }), PsiReferenceRegistrar.HIGHER_PRIORITY);
  }
}
