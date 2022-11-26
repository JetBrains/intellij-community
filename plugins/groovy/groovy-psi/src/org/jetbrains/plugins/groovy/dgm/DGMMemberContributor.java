// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dgm;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_INSTANCE_EXTENSIONS;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_STATIC_EXTENSIONS;

/**
 * Provides members from extension classes referenced in {@code META-INF/services/org.codehaus.groovy.runtime.ExtensionModule}.
 */
public final class DGMMemberContributor {

  public static boolean processDgmMethods(@NotNull PsiType qualifierType,
                                          @NotNull PsiScopeProcessor processor,
                                          @NotNull PsiElement place,
                                          @NotNull ResolveState state) {
    if (!ResolveUtil.shouldProcessMethods(processor.getHint(ElementClassHint.KEY))) return true;

    final Project project = place.getProject();

    ConcurrentMap<GlobalSearchScope, List<GdkMethodHolder>> map = CachedValuesManager.getManager(project).getCachedValue(
      project, () -> {
        ConcurrentMap<GlobalSearchScope, List<GdkMethodHolder>> value = ContainerUtil.createConcurrentSoftValueMap();
        return CachedValueProvider.Result.create(value, PsiModificationTracker.MODIFICATION_COUNT);
      });

    GlobalSearchScope scope = place.getResolveScope();
    List<GdkMethodHolder> gdkMethods = map.get(scope);
    if (gdkMethods == null) {
      map.put(scope, gdkMethods = calcGdkMethods(project, scope));
    }

    for (GdkMethodHolder holder : gdkMethods) {
      if (!holder.processMethods(processor, state, qualifierType, project)) {
        return false;
      }
    }

    if (!resolvesToMacro(processor, state, place, project)) {
      return false;
    }

    return true;
  }

  private static boolean resolvesToMacro(PsiScopeProcessor processor, ResolveState state, @NotNull PsiElement place, Project project) {
    GroovyMacroRegistryService macroService = project.getService(GroovyMacroRegistryService.class);
    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint == null ? null : nameHint.getName(state);
    if (name == null) {
      return true;
    }

    Collection<PsiMethod> macros = macroService.getAllKnownMacros(place);
    for (PsiMethod macro : macros) {
      if (!processor.execute(GdkMethodUtil.createMacroMethod(macro), state)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static List<GdkMethodHolder> calcGdkMethods(Project project, GlobalSearchScope resolveScope) {
    List<GdkMethodHolder> gdkMethods = new ArrayList<>();

    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    Couple<List<String>> extensions = collectExtensions(project, resolveScope);
    for (String category : extensions.getFirst()) {
      PsiClass clazz = facade.findClass(category, resolveScope);
      if (clazz != null) {
        gdkMethods.add(GdkMethodHolder.getHolderForClass(clazz, false));
      }
    }
    for (String category : extensions.getSecond()) {
      PsiClass clazz = facade.findClass(category, resolveScope);
      if (clazz != null) {
        gdkMethods.add(GdkMethodHolder.getHolderForClass(clazz, true));
      }
    }
    return gdkMethods;
  }

  @NotNull
  private static Couple<List<String>> collectExtensions(@NotNull Project project, @NotNull GlobalSearchScope resolveScope) {
    List<String> instanceClasses = new ArrayList<>(DEFAULT_INSTANCE_EXTENSIONS);
    List<String> staticClasses = new ArrayList<>(DEFAULT_STATIC_EXTENSIONS);
    doCollectExtensions(project, resolveScope, instanceClasses, staticClasses, "META-INF.groovy");
    doCollectExtensions(project, resolveScope, instanceClasses, staticClasses, "META-INF.services");
    return Couple.of(instanceClasses, staticClasses);
  }

  private static void doCollectExtensions(@NotNull Project project,
                                          @NotNull GlobalSearchScope resolveScope,
                                          @NotNull List<? super String> instanceClasses,
                                          @NotNull List<? super String> staticClasses,
                                          @NlsSafe @NotNull String packageName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
    if (aPackage == null) return;

    for (PsiDirectory directory : aPackage.getDirectories(resolveScope)) {
      PsiFile file = directory.findFile(DGMUtil.ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE);
      if (!(file instanceof PropertiesFile)) continue;
      AstLoadingFilter.forceAllowTreeLoading(file, () -> {
        IProperty inst = ((PropertiesFile)file).findPropertyByKey("extensionClasses");
        IProperty stat = ((PropertiesFile)file).findPropertyByKey("staticExtensionClasses");

        if (inst != null) collectClasses(inst, instanceClasses);
        if (stat != null) collectClasses(stat, staticClasses);
      });
    }
  }

  private static void collectClasses(@NotNull IProperty pr, @NotNull List<? super String> classes) {
    String value = pr.getUnescapedValue();
    if (value == null) return;
    value = value.trim();
    String[] qnames = value.split("\\s*,\\s*");
    ContainerUtil.addAll(classes, qnames);
  }
}
