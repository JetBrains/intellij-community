// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.ide.presentation.Presentation;
import com.intellij.java.library.JavaLibraryModificationTracker;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uast.UastModificationTracker;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.UastUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.literalExpression;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiExpression;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.patterns.uast.UastPatterns.injectionHostUExpression;
import static com.intellij.psi.UastReferenceRegistrar.registerUastReferenceProvider;
import static com.intellij.psi.UastReferenceRegistrar.uastInjectionHostReferenceProvider;
import static java.util.Collections.emptySet;
import static org.jetbrains.idea.devkit.references.IconsReferencesQueryExecutor.ALL_ICONS_FQN;
import static org.jetbrains.idea.devkit.references.IconsReferencesQueryExecutor.COM_INTELLIJ_ICONS_PREFIX;
import static org.jetbrains.idea.devkit.references.IconsReferencesQueryExecutor.ICONS_MODULE;
import static org.jetbrains.idea.devkit.references.IconsReferencesQueryExecutor.ICONS_PACKAGE_PREFIX;
import static org.jetbrains.idea.devkit.references.IconsReferencesQueryExecutor.IconPsiReferenceBase;
import static org.jetbrains.idea.devkit.references.IconsReferencesQueryExecutor.PLATFORM_ICONS_MODULE;
import static org.jetbrains.idea.devkit.references.IconsReferencesQueryExecutor.resolveIconPath;

final class IconsUastReferencesContributor extends PsiReferenceContributor {

  public static final String ALL_ICONS_RESOURCES_MODULE = "intellij.platform.ide";

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registerForPresentationAnnotation(registrar);
    registerForIconLoaderMethods(registrar);
  }

  private static void registerForIconLoaderMethods(@NotNull PsiReferenceRegistrar registrar) {
    var method = psiMethod().withName("load").definedInClass(psiClass().withName(string().endsWith("Icons")));
    var findGetIconPattern = literalExpression().and(psiExpression().methodCallParameter(0, method));
    registrar.registerReferenceProvider(findGetIconPattern, new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (containingClass == null) return PsiReference.EMPTY_ARRAY;

        if (!IntelliJProjectUtil.isIntelliJPlatformProject(element.getProject())) {
          // when it is a plugin project with Gradle build we resolve to libs
          return new FileReferenceSet(element) {
            @Override
            public @NotNull Collection<PsiFileSystemItem> getDefaultContexts() {
              return getIconsClassLibraryRoots(element.getProject(), containingClass);
            }
          }.getAllReferences();
        }

        String containingClassQualifiedName = containingClass.getQualifiedName();
        if (containingClassQualifiedName == null || !containingClassQualifiedName.startsWith(ALL_ICONS_FQN)) {
          return PsiReference.EMPTY_ARRAY;
        }

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

  private static @NotNull Collection<PsiFileSystemItem> getIconsClassLibraryRoots(@NotNull Project project, @NotNull PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, () -> {
      return Result.create(findIconsClassLibraryRoots(project, psiClass),
                           JavaLibraryModificationTracker.getInstance(project),
                           UastModificationTracker.getInstance(project));
    });
  }

  private static @NotNull Collection<PsiFileSystemItem> findIconsClassLibraryRoots(@NotNull Project project, @NotNull PsiClass psiClass) {
    var compiledClassOrOriginal = psiClass.getOriginalElement();
    PsiFile containingFile = compiledClassOrOriginal.getContainingFile();
    if (containingFile == null) return emptySet();

    var virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return emptySet();

    var root = JarFileSystem.getInstance().getRootByEntry(virtualFile);
    if (root == null) return emptySet();

    var roots = new ArrayList<PsiFileSystemItem>();
    var directory = PsiManager.getInstance(project).findDirectory(root);
    if (directory != null) {
      roots.add(directory);
    }

    // starting 2026.2, lookup in intellij.platform.ide JAR
    OrderEnumerator.orderEntries(project)
      .forEachLibrary(library -> {
        for (VirtualFile libraryFile : library.getFiles(OrderRootType.CLASSES)) {
          if (ALL_ICONS_RESOURCES_MODULE.equals(libraryFile.getNameWithoutExtension())) {
            PsiDirectory jarRoot = PsiManager.getInstance(project).findDirectory(libraryFile);
            if (jarRoot != null) {
              roots.add(jarRoot);
            }
          }
        }

        return true;
      });

    return Set.copyOf(roots);
  }

  private static void registerForPresentationAnnotation(@NotNull PsiReferenceRegistrar registrar) {
    registerUastReferenceProvider(
      registrar,
      injectionHostUExpression()
        .sourcePsiFilter(psi -> PsiUtil.isPluginProject(psi.getProject()))
        .annotationParam(Presentation.class.getName(), "icon"),
      uastInjectionHostReferenceProvider((uElement, referencePsiElement) -> new PsiReference[]{
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
