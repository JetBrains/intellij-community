// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.completion.CompletionConfidenceEP;
import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ProjectIconsAccessor;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.uast.UastModificationTracker;
import com.intellij.ui.IconManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomJavaUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.*;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

final class LanguageResolvingUtil {
  private static final String ANY_LANGUAGE_DEFAULT_ID = Language.ANY.getID();

  static Collection<LanguageDefinition> getAllLanguageDefinitions(ConvertContext context) {
    List<LanguageDefinition> languageDefinitions = collectLanguageDefinitions(context);
    ContainerUtil.addIfNotNull(languageDefinitions, createAnyLanguageDefinition(context));
    return languageDefinitions;
  }

  static boolean processAllLanguageDefinitions(ConvertContext context, Processor<? super LanguageDefinition> processor) {
    if (!processLanguageDefinitions(context, processor)) {
      return false;
    }
    LanguageDefinition any = createAnyLanguageDefinition(context);
    return any != null && processor.process(any);
  }

  private static boolean processLanguageDefinitions(ConvertContext context, Processor<? super LanguageDefinition> processor) {
    Project project = context.getProject();
    GlobalSearchScope projectProductionScope = GlobalSearchScopesCore.projectProductionScope(project);
    GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(project);

    // force finding inside IDEA project first
    PsiClass languageClass = JavaPsiFacade.getInstance(project).findClass(Language.class.getName(), projectProductionScope);
    if (languageClass == null) {
      languageClass = JavaPsiFacade.getInstance(project).findClass(Language.class.getName(), librariesScope);
    }
    Iterable<PsiClass> allLanguages;
    List<LanguageDefinition> libraryDefinitions;
    if (languageClass == null) {
      allLanguages = Collections.emptyList();
      libraryDefinitions = Collections.emptyList();
    }
    else {
      GlobalSearchScope allScope = projectProductionScope.union(librariesScope);
      allLanguages = ClassInheritorsSearch.search(languageClass, allScope, true);
      libraryDefinitions = collectLibraryLanguages(context, allScope);
    }

    if (!ContainerUtil.process(libraryDefinitions, processor)) {
      return false;
    }
    return
    StreamSupport.stream(allLanguages.spliterator(), false)
      .filter(aClass -> PsiSearchScopeUtil.isInScope(projectProductionScope, aClass))
      .map(language -> getDefinition(language, libraryDefinitions))
      .filter(Objects::nonNull)
      .allMatch(processor::process);
  }

  private static List<LanguageDefinition> collectLanguageDefinitions(ConvertContext context) {
    Project project = context.getProject();
    GlobalSearchScope projectProductionScope = GlobalSearchScopesCore.projectProductionScope(project);
    GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(project);
    GlobalSearchScope allScope = projectProductionScope.union(librariesScope);
    Collection<PsiClass> allLanguages =
      CachedValuesManager.getManager(project).getCachedValue(project, () -> {

        // force finding inside IDEA project first
        PsiClass languageClass = JavaPsiFacade.getInstance(project).findClass(Language.class.getName(), projectProductionScope);
        if (languageClass == null) {
          languageClass = JavaPsiFacade.getInstance(project).findClass(Language.class.getName(), librariesScope);
        }
        if (languageClass == null) {
          return Result.create(Collections.emptyList(), UastModificationTracker.getInstance(project));
        }

        Collection<PsiClass> allInheritors = new HashSet<>(ClassInheritorsSearch.search(languageClass, allScope, true).findAll());
        return Result.create(allInheritors, UastModificationTracker.getInstance(project));
      });
    if (allLanguages.isEmpty()) {
      return new SmartList<>();
    }

    List<LanguageDefinition> libraryDefinitions = collectLibraryLanguages(context, allScope);

    Collection<PsiClass> projectLanguages =
      ContainerUtil.filter(allLanguages, aClass -> PsiSearchScopeUtil.isInScope(projectProductionScope, aClass));
    List<LanguageDefinition> projectDefinitions = mapToProjectDefinitions(projectLanguages, libraryDefinitions);

    List<LanguageDefinition> all = new ArrayList<>(libraryDefinitions);
    all.addAll(projectDefinitions);
    return all;
  }

  private static List<LanguageDefinition> collectLibraryLanguages(ConvertContext context, GlobalSearchScope scope) {
    return ContainerUtil.mapNotNull(Language.getRegisteredLanguages(), language -> getLibraryDefinition(context, language, scope));
  }

  @Nullable
  private static LanguageDefinition getLibraryDefinition(ConvertContext context,
                                                         Language language,
                                                         @NotNull GlobalSearchScope scope) {
    if (language.getID().isEmpty()) {
      return null;
    }
    PsiClass psiClass = DomJavaUtil.findClass(language.getClass().getName(), context.getInvocationElement(), true);
    if (psiClass == null || !PsiSearchScopeUtil.isInScope(scope, psiClass)) {
      return null;
    }

    return new LanguageDefinition(language.getID(), psiClass, () -> {
      LanguageFileType type = language.getAssociatedFileType();
      return type == null ? null : type.getIcon();
    }, () -> language.getDisplayName());
  }

  private static List<LanguageDefinition> mapToProjectDefinitions(Collection<? extends PsiClass> projectLanguages,
                                                                  List<? extends LanguageDefinition> libraryDefinitions) {
    return ContainerUtil.mapNotNull(projectLanguages, language -> getDefinition(language, libraryDefinitions));
  }

  private static LanguageDefinition getDefinition(@NotNull PsiClass language, List<? extends LanguageDefinition> libraryLanguages) {
    if (language.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return null;
    }

    if (ContainerUtil.exists(libraryLanguages, definition -> definition.clazz.equals(language))) {
      return null;
    }

    return CachedValuesManager.getCachedValue(language, () -> {
      String languageId = computeConstantSuperCtorCallParameter(language, 0);
      if (languageId == null) {
        languageId = computeConstantSuperCtorCallParameter(language, 1);
      }
      if (languageId == null) {
        languageId = computeConstantReturnValue(language, "getID");
      }
      if (StringUtil.isEmpty(languageId)) {
        return Result.create(null, language);
      }

      LanguageDefinition definition =
        new LanguageDefinition(languageId, language,
                               () -> computeIconForProjectLanguage(language),
                               () -> computeConstantReturnValue(language, "getDisplayName"));

      return Result.createSingleDependency(definition, language);
    });
  }

  @Nullable
  private static Icon computeIconForProjectLanguage(PsiClass language) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(language);
    if (module == null) return null;

    Project project = language.getProject();
    final PsiClass languageFileType = JavaPsiFacade.getInstance(project).findClass(LanguageFileType.class.getName(),
                                                                                   language.getResolveScope());
    if (languageFileType == null) return null;

    PsiClass fileType = findLanguageFileType(language, module, languageFileType);
    if (fileType == null) return null;

    final ProjectIconsAccessor iconsAccessor = ProjectIconsAccessor.getInstance(project);
    final UExpression icon = getReturnExpression(fileType, "getIcon");
    final VirtualFile iconFile = iconsAccessor.resolveIconFile(icon);
    return iconFile == null ? null : iconsAccessor.getIcon(iconFile);
  }

  /**
   * Order:
   * - overridden Language.getAssociatedFileType()
   * - matching LFT in current module
   * - matching LFT in resolve scope
   */
  @Nullable
  private static PsiClass findLanguageFileType(PsiClass language, Module module, PsiClass languageFileType) {
    final UExpression associatedFileTypeExpression = getReturnExpression(language, "getAssociatedFileType");
    if (associatedFileTypeExpression != null) {
      return PsiTypesUtil.getPsiClass(associatedFileTypeExpression.getExpressionType());
    }

    final GlobalSearchScope currentModuleScope = GlobalSearchScope.moduleWithDependenciesScope(module);
    PsiClass matchingFileType = findMatchingFileType(languageFileType, language, currentModuleScope);
    if (matchingFileType != null) return matchingFileType;

    return findMatchingFileType(languageFileType, language, language.getUseScope());
  }

  @Nullable
  private static PsiClass findMatchingFileType(PsiClass languageFileType,
                                               PsiClass language,
                                               SearchScope scope) {
    return ClassInheritorsSearch.search(languageFileType, scope, true).filtering(psiClass -> {
      UClass uClass = UastContextKt.toUElement(psiClass, UClass.class);
      if (uClass == null) return false;

      final UCallExpression expression = getSuperConstructorParameterExpression(uClass);
      if (expression == null) return false;

      final UExpression languageParam = expression.getArgumentForParameter(0);
      if (languageParam == null) return false;

      final PsiClass paramPsiClass = PsiTypesUtil.getPsiClass(languageParam.getExpressionType());
      return language.getManager().areElementsEquivalent(language, paramPsiClass);
    }).findFirst();
  }

  @Nullable
  private static String computeConstantReturnValue(PsiClass psiClass,
                                                   String methodName) {
    final UExpression expression = getReturnExpression(psiClass, methodName);
    if (expression == null) return null;

    return UastUtils.evaluateString(expression);
  }

  @Nullable
  private static UExpression getReturnExpression(PsiClass psiClass, String methodName) {
    final PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
    if (methods.length != 1) {
      return null;
    }

    return PsiUtil.getReturnedExpression(methods[0]);
  }

  private static String computeConstantSuperCtorCallParameter(PsiClass languagePsiClass, int index) {
    UClass languageClass = UastContextKt.toUElement(languagePsiClass, UClass.class);
    if (languageClass == null) return null;
    if (languageClass instanceof UAnonymousClass) {
      return getStringConstantExpression(UastUtils.findContaining(languageClass.getSourcePsi(), UObjectLiteralExpression.class), index);
    }

    UCallExpression methodCallExpression = getSuperConstructorParameterExpression(languageClass);
    return getStringConstantExpression(methodCallExpression, index);
  }

  @Nullable
  private static UCallExpression getSuperConstructorParameterExpression(UClass languageClass) {
    UMethod defaultConstructor = ContainerUtil.find(languageClass.getMethods(),
                                                    method -> method.isConstructor() && method.getUastParameters().isEmpty());
    if (defaultConstructor == null) {
      return null;
    }

    final UExpression body = defaultConstructor.getUastBody();
    if (!(body instanceof UBlockExpression)) {
      return null;
    }
    final List<UExpression> expressions = ((UBlockExpression)body).getExpressions();

    // super() must be first
    UExpression expression = ContainerUtil.getFirstItem(expressions);
    return ObjectUtils.tryCast(expression, UCallExpression.class);
  }

  @Nullable
  private static String getStringConstantExpression(@Nullable UCallExpression callExpression, int index) {
    if (callExpression == null) {
      return null;
    }
    UExpression argument = callExpression.getArgumentForParameter(index);
    if (argument == null) {
      return null;
    }
    return UastUtils.evaluateString(argument);
  }

  @Nullable
  private static LanguageDefinition createAnyLanguageDefinition(ConvertContext context) {
    final PsiClass languageClass = DomJavaUtil.findClass(Language.class.getName(), context.getInvocationElement());
    if (languageClass == null) return null;

    String anyLanguageId = calculateAnyLanguageId(context);
    return new LanguageDefinition(anyLanguageId, languageClass,
                                  () -> AllIcons.FileTypes.Any_type,
                                  () -> DevKitBundle.message("plugin.xml.convert.language.id.any.language.display.name"));
  }

  private static final Set<String> EP_WITH_ANY_LANGUAGE_ID = Collections
    .unmodifiableSet(ContainerUtil.set(CompletionContributorEP.class.getName(), CompletionConfidenceEP.class.getName()));

  private static @NlsSafe String calculateAnyLanguageId(@NotNull ConvertContext context) {
    final Extension extension = context.getInvocationElement().getParentOfType(Extension.class, true);
    if (extension == null) {
      return ANY_LANGUAGE_DEFAULT_ID;
    }
    final ExtensionPoint extensionPoint = extension.getExtensionPoint();
    if (extensionPoint == null) {
      return ANY_LANGUAGE_DEFAULT_ID;
    }

    final GenericAttributeValue<PsiClass> epBeanClass = extensionPoint.getBeanClass();
    if (EP_WITH_ANY_LANGUAGE_ID.contains(epBeanClass.getStringValue())) {
      return "any";
    }

    return ANY_LANGUAGE_DEFAULT_ID;
  }

  static class LanguageDefinition {
    final String id;
    final PsiClass clazz;
    final Icon icon;
    final Supplier<String> displayName;
    final String type;

    LanguageDefinition(@NotNull String id, @NotNull PsiClass clazz,
                       @NotNull Supplier<? extends @Nullable Icon> iconSupplier,
                       Supplier<String> displayName) {
      this.id = id;
      this.clazz = clazz;
      this.type = clazz instanceof PsiAnonymousClass
                  ? ((PsiAnonymousClass)clazz).getBaseClassReference().getQualifiedName()
                  : clazz.getQualifiedName();
      this.icon = IconManager.getInstance().createDeferredIcon(EmptyIcon.ICON_16, iconSupplier,
                                                               (Function<Supplier<? extends Icon>, Icon>)supplier -> supplier.get());
      this.displayName = displayName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof LanguageDefinition)) return false;
      return clazz.equals(((LanguageDefinition)o).clazz);
    }

    @Override
    public int hashCode() {
      return clazz.hashCode();
    }
  }
}
