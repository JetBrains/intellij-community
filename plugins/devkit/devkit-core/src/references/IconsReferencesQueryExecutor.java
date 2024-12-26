// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.ProjectIconsAccessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UastContextKt;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class IconsReferencesQueryExecutor implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {

  static final @NonNls String ALL_ICONS_FQN = "com.intellij.icons.AllIcons";
  static final @NonNls String ALL_ICONS_NAME = "AllIcons";
  static final @NonNls String PLATFORM_ICONS_MODULE = "intellij.platform.icons";
  static final @NonNls String ICONS_MODULE = "icons";

  static final @NonNls String ICONS_PACKAGE_PREFIX = "icons.";
  static final @NonNls String COM_INTELLIJ_ICONS_PREFIX = "com.intellij.icons.";
  static final @NonNls String ICONS_CLASSNAME_SUFFIX = "Icons";

  @Override
  public boolean execute(@NotNull ReferencesSearch.SearchParameters queryParameters,
                         final @NotNull Processor<? super PsiReference> consumer) {
    final PsiElement file = queryParameters.getElementToSearch();
    if (file instanceof PsiBinaryFile) {
      final Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(file));

      final VirtualFile image = ((PsiBinaryFile)file).getVirtualFile();
      if (isImage(image) && isIconsModule(module)) {
        final Project project = file.getProject();
        final FindModel model = new FindModel();
        final String path = getPathToImage(image, module);
        model.setStringToFind(path);
        model.setCaseSensitive(true);
        model.setFindAll(true);
        model.setWholeWordsOnly(true);
        FindInProjectUtil.findUsages(model, project, usage -> {
          ApplicationManager.getApplication().runReadAction(() -> {
            final PsiElement element = usage.getElement();

            final ProperTextRange textRange = usage.getRangeInElement();
            if (element != null && textRange != null) {
              final PsiElement start = element.findElementAt(textRange.getStartOffset());
              final PsiElement end = element.findElementAt(textRange.getEndOffset());
              if (start != null && end != null) {
                PsiElement value = PsiTreeUtil.findCommonParent(start, end);
                if (value instanceof PsiJavaToken) {
                  value = value.getParent();
                }
                if (value != null) {
                  final PsiFileReference reference = FileReferenceUtil.findFileReference(value);
                  if (reference != null) {
                    consumer.process(reference);
                  }
                }
              }
            }
          });
          return true;
        }, new FindUsagesProcessPresentation(new UsageViewPresentation()));
      }
    }
    return true;
  }

  private static @NotNull @NonNls String getPathToImage(VirtualFile image, Module module) {
    final String path = ModuleRootManager.getInstance(module).getSourceRoots()[0].getPath();
    return "/" + FileUtil.getRelativePath(path, image.getPath(), '/');
  }

  private static boolean isIconsModule(Module module) {
    return module != null && (ICONS_MODULE.equals(module.getName()) || PLATFORM_ICONS_MODULE.equals(module.getName()))
           && ModuleRootManager.getInstance(module).getSourceRoots().length == 1;
  }

  private static boolean isImage(VirtualFile image) {
    final FileTypeManager mgr = FileTypeManager.getInstance();
    return image != null && mgr.getFileTypeByFile(image) == mgr.getFileTypeByExtension("png");
  }

  /**
   * Name of class containing icons must end with {@code Icons}.
   * <p>
   * Valid icon paths:
   * <ul>
   * <li>AllIcons.IconFieldName (=com.intellij.icons.AllIcons)</li>
   * <li>MyIcons.IconFieldName (implicitly in 'icons' package)</li>
   * <li>MyIcons.InnerClass.IconFieldName ("")</li>
   * </ul>
   * Using FQN notation:
   * <ul>
   * <li>com.company.MyIcons.IconFieldName</li>
   * <li>com.company.MyIcons.InnerClass.IconFieldName</li>
   * </ul>
   */
  public static @Nullable PsiField resolveIconPath(@NonNls @Nullable String path, PsiElement element) {
    if (path == null) return null;

    @NonNls List<String> pathElements = StringUtil.split(path, ".");
    if (pathElements.size() < 2) return null;

    final int iconsClassNameIdx = ContainerUtil.lastIndexOf(pathElements, s -> s.endsWith(ICONS_CLASSNAME_SUFFIX));
    if (iconsClassNameIdx == -1) return null;

    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) return null;

    PsiClass iconClass = findIconClass(module,
                                       StringUtil.join(ContainerUtil.getFirstItems(pathElements, iconsClassNameIdx + 1), "."),
                                       iconsClassNameIdx != 0);
    if (iconClass == null) return null;

    for (int i = iconsClassNameIdx + 1; i < pathElements.size() - 1; i++) {
      iconClass = iconClass.findInnerClassByName(pathElements.get(i), false);
      if (iconClass == null) return null;
    }

    return iconClass.findFieldByName(pathElements.get(pathElements.size() - 1), false);
  }

  private static @Nullable PsiClass findIconClass(Module module, @NonNls @NotNull String iconClass, boolean isQualifiedFqn) {
    final String adjustedIconClassFqn;
    if (isQualifiedFqn) {
      adjustedIconClassFqn = iconClass;
    }
    else {
      adjustedIconClassFqn = ALL_ICONS_NAME.equals(iconClass) ? ALL_ICONS_FQN : ICONS_PACKAGE_PREFIX + iconClass;
    }

    GlobalSearchScope iconSearchScope = isQualifiedFqn ? GlobalSearchScope.moduleRuntimeScope(module, false) :
                                        GlobalSearchScope.allScope(module.getProject());
    return JavaPsiFacade.getInstance(module.getProject()).findClass(adjustedIconClassFqn, iconSearchScope);
  }

  abstract static class IconPsiReferenceBase extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider {

    IconPsiReferenceBase(@NotNull PsiElement element) {
      super(element, true);
    }

    @Override
    public Object @NotNull [] getVariants() {
      Module module = ModuleUtilCore.findModuleForPsiElement(myElement);
      if (module == null) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
      final Project project = module.getProject();
      final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);

      GlobalSearchScope productionScope = GlobalSearchScope.moduleRuntimeScope(module, false);

      List<LookupElement> variants = new ArrayList<>();

      // AllIcons
      addIconVariants(variants, javaPsiFacade.findClass(ALL_ICONS_FQN, productionScope), false, Collections.emptyList());

      // icons.*Icons
      final PsiPackage iconsPackage = javaPsiFacade.findPackage("icons");
      if (iconsPackage != null) {
        for (PsiClass psiClass : iconsPackage.getClasses(productionScope)) {
          final String className = psiClass.getName();
          if (className == null || !StringUtil.endsWith(className, ICONS_CLASSNAME_SUFFIX)) continue;

          addIconVariants(variants, psiClass, false, Collections.emptyList());
        }
      }

      // FQN *Icons
      GlobalSearchScope notIconsPackageScope =
        iconsPackage != null ? PackageScope.packageScope(iconsPackage, false) : GlobalSearchScope.EMPTY_SCOPE;
      final Query<PsiClass> allIconsSearch =
        AllClassesSearch.search(productionScope.intersectWith(GlobalSearchScope.notScope(notIconsPackageScope)),
                                project, s -> StringUtil.endsWith(s, ICONS_CLASSNAME_SUFFIX) && !s.equals(ICONS_CLASSNAME_SUFFIX));
      allIconsSearch.forEach(psiClass -> {
        if (ALL_ICONS_FQN.equals(psiClass.getQualifiedName()) ||
            psiClass.isInterface() ||
            psiClass.getContainingClass() != null ||
            !psiClass.hasModifier(JvmModifier.PUBLIC)) {
          return;
        }

        addIconVariants(variants, psiClass, true, Collections.emptyList());
      });
      return variants.toArray();
    }

    private static void addIconVariants(List<LookupElement> variants,
                                        @Nullable PsiClass iconClass,
                                        boolean useFqn,
                                        List<PsiClass> containingClasses) {
      if (iconClass == null) return;

      String classNamePrefix;
      if (useFqn) {
        classNamePrefix = containingClasses.isEmpty() ? iconClass.getQualifiedName() :
                          ContainerUtil.getLastItem(containingClasses).getQualifiedName() + "." + iconClass.getName();
      }
      else {
        classNamePrefix = containingClasses.isEmpty() ? iconClass.getName() :
                          StringUtil.join(containingClasses, aClass -> aClass.getName(), ".") + "." + iconClass.getName();
      }

      for (PsiField field : iconClass.getFields()) {
        if (!ProjectIconsAccessor.isIconClassType(field.getType())) continue;

        String iconPath = classNamePrefix + "." + field.getName();
        LookupElementBuilder builder = LookupElementBuilder.create(field, iconPath)
          .withRenderer(IconPathLookupElementRenderer.INSTANCE);
        variants.add(builder);
      }

      final List<PsiClass> parents = new SmartList<>(containingClasses);
      parents.add(iconClass);
      for (PsiClass innerClass : iconClass.getInnerClasses()) {
        addIconVariants(variants, innerClass, useFqn, parents);
      }
    }


    @SuppressWarnings("UnresolvedPropertyKey")
    @Override
    public @NotNull String getUnresolvedMessagePattern() {
      return DevKitBundle.message("inspections.presentation.cannot.resolve.icon");
    }


    private static class IconPathLookupElementRenderer extends LookupElementRenderer<LookupElement> {

      private static final IconPathLookupElementRenderer INSTANCE = new IconPathLookupElementRenderer();

      @Override
      public void renderElement(LookupElement element, LookupElementPresentation presentation) {
        final PsiField field = ObjectUtils.tryCast(element.getPsiElement(), PsiField.class);
        assert field != null;

        final String iconPath = element.getLookupString();
        presentation.setItemText(iconPath);

        presentation.setStrikeout(field.isDeprecated());

        final Icon resolveIcon = resolveIcon(field, iconPath);
        if (resolveIcon != null) {
          if (ProjectIconsAccessor.hasProperSize(resolveIcon)) {
            presentation.setIcon(resolveIcon);
          }
          else {
            presentation.setTailText(" (" + resolveIcon.getIconWidth() + "x" + resolveIcon.getIconHeight() + ")", true);
          }
        }
      }

      private static @Nullable Icon resolveIcon(PsiField field, @NotNull String iconPath) {
        UField uField = UastContextKt.toUElement(field, UField.class);
        assert uField != null;
        UExpression expression = uField.getUastInitializer();
        if (expression == null) {
          return IconLoader.findIcon(iconPath, IconPsiReferenceBase.class, false, false);
        }

        ProjectIconsAccessor iconsAccessor = ProjectIconsAccessor.getInstance(field.getProject());
        VirtualFile iconFile = iconsAccessor.resolveIconFile(expression);
        return iconFile == null ? null : iconsAccessor.getIcon(iconFile);
      }
    }
  }
}
