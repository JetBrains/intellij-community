// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaCoverageSuite extends BaseCoverageSuite {
  @NonNls
  private static final String FILTER = "FILTER";
  @NonNls
  private static final String EXCLUDED_FILTER = "EXCLUDED_FILTER";
  @NonNls
  private static final String COVERAGE_RUNNER = "RUNNER";
  private final CoverageEngine myCoverageEngine;
  private String @Nullable [] myIncludeFilters;
  private String @Nullable [] myExcludePatterns;
  private boolean mySkipUnloadedClassesAnalysis;

  //read external only
  public JavaCoverageSuite(@NotNull final CoverageEngine coverageEngine) {
    super();
    myCoverageEngine = coverageEngine;
  }

  public JavaCoverageSuite(final String name,
                           final CoverageFileProvider coverageDataFileProvider,
                           final String @Nullable [] includeFilters,
                           final String @Nullable [] excludePatterns,
                           final long lastCoverageTimeStamp,
                           final boolean coverageByTestEnabled,
                           final boolean branchCoverage,
                           final boolean trackTestFolders,
                           final CoverageRunner coverageRunner,
                           @NotNull final CoverageEngine coverageEngine,
                           final Project project) {
    super(name, coverageDataFileProvider, lastCoverageTimeStamp, coverageByTestEnabled,
          branchCoverage, trackTestFolders,
          coverageRunner != null ? coverageRunner : CoverageRunner.getInstance(IDEACoverageRunner.class), project);

    myCoverageEngine = coverageEngine;
    myIncludeFilters = includeFilters;
    myExcludePatterns = excludePatterns;

    if (coverageRunner instanceof JaCoCoCoverageRunner) {
      setSkipUnloadedClassesAnalysis(true);
    }
  }

  public final String @NotNull [] getFilteredPackageNames() {
    return getPackageNames(myIncludeFilters);
  }

  public final String @NotNull [] getExcludedPackageNames() {
    return getPackageNames(myExcludePatterns);
  }

  final String @Nullable [] getIncludeFilters() {
    return myIncludeFilters;
  }

  final void setIncludeFilters(String @Nullable [] filters) {
    myIncludeFilters = filters;
  }

  final String @Nullable [] getExcludePatterns() {
    return myExcludePatterns;
  }

  final void setExcludePatterns(String @Nullable [] patterns) {
    myExcludePatterns = patterns;
  }

  public boolean isSkipUnloadedClassesAnalysis() {
    return mySkipUnloadedClassesAnalysis;
  }

  public void setSkipUnloadedClassesAnalysis(boolean skipUnloadedClassesAnalysis) {
    mySkipUnloadedClassesAnalysis = skipUnloadedClassesAnalysis;
  }

  private static String[] getPackageNames(String[] filters) {
    if (filters == null || filters.length == 0) return ArrayUtilRt.EMPTY_STRING_ARRAY;
    List<String> result = new ArrayList<>();
    for (String filter : filters) {
      if (filter.equals("*")) {
        result.add(""); //default package
      }
      else if (filter.endsWith(".*")) result.add(filter.substring(0, filter.length() - 2));
    }
    return ArrayUtilRt.toStringArray(result);
  }

  public final String @NotNull [] getFilteredClassNames() {
    return getClassNames(myIncludeFilters);
  }

  public final String @NotNull [] getExcludedClassNames() {
    return getClassNames(myExcludePatterns);
  }

  private static String @NotNull [] getClassNames(final String[] filters) {
    if (filters == null) return ArrayUtilRt.EMPTY_STRING_ARRAY;
    List<String> result = new ArrayList<>();
    for (String filter : filters) {
      if (!filter.equals("*") && !filter.endsWith(".*")) result.add(filter);
    }
    return ArrayUtilRt.toStringArray(result);
  }

  @Override
  public final void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    // filters
    myIncludeFilters = readFilters(element, FILTER);
    myExcludePatterns = readFilters(element, EXCLUDED_FILTER);

    if (getRunner() == null) {
      setRunner(CoverageRunner.getInstance(IDEACoverageRunner.class)); //default
    }
  }

  private static String[] readFilters(Element element, final String tagName) {
    final List<Element> children = element.getChildren(tagName);
    List<String> filters = new ArrayList<>();
    for (Element child : children) {
      filters.add(child.getValue());
    }
    return filters.isEmpty() ? null : ArrayUtilRt.toStringArray(filters);
  }

  @Override
  public final void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeFilters(element, myIncludeFilters, FILTER);
    writeFilters(element, myExcludePatterns, EXCLUDED_FILTER);
    final CoverageRunner coverageRunner = getRunner();
    if (coverageRunner != null) {
      element.setAttribute(COVERAGE_RUNNER, coverageRunner.getId());
    }
  }

  private static void writeFilters(Element element, final String[] filters, final String tagName) {
    if (filters != null) {
      for (String filter : filters) {
        final Element filterElement = new Element(tagName);
        filterElement.setText(filter);
        element.addContent(filterElement);
      }
    }
  }

  @Override
  @NotNull
  public final CoverageEngine getCoverageEngine() {
    return myCoverageEngine;
  }

  public final boolean isClassFiltered(final String classFQName) {
    return isClassFiltered(classFQName, getFilteredClassNames());
  }

  public static boolean isClassFiltered(String classFQName, String[] classPatterns) {
    for (String className : classPatterns) {
      if (className.equals(classFQName) || classFQName.startsWith(className) && classFQName.charAt(className.length()) == '$') {
        return true;
      }
    }
    return false;
  }

  public final boolean isPackageFiltered(final String packageFQName) {
    for (String name : getExcludedPackageNames()) {
      if (packageFQName.equals(name) || packageFQName.startsWith(name + ".")) return false;
    }
    final String[] filteredPackageNames = getFilteredPackageNames();
    for (final String packName : filteredPackageNames) {
      if (packName.isEmpty() || PsiNameHelper.isSubpackageOf(packageFQName, packName)) {
        return true;
      }
    }
    return filteredPackageNames.length == 0 && getFilteredClassNames().length == 0;
  }

  public final @NotNull List<PsiPackage> getCurrentSuitePackages(final Project project) {
    return ReadAction.compute(() -> {
      final List<PsiPackage> packages = new ArrayList<>();
      final PsiManager psiManager = PsiManager.getInstance(project);
      final String[] filters = getFilteredPackageNames();
      if (filters.length == 0) {
        if (getFilteredClassNames().length > 0) return Collections.emptyList();

        final PsiPackage defaultPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage("");
        if (defaultPackage != null) {
          packages.add(defaultPackage);
        }
      }
      else {
        final List<String> nonInherited = new ArrayList<>();
        for (final String filter : filters) {
          if (!isSubPackage(filters, filter)) {
            nonInherited.add(filter);
          }
        }

        for (String filter : nonInherited) {
          final PsiPackage psiPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(filter);
          if (psiPackage != null) {
            packages.add(psiPackage);
          }
        }
      }
      return packages;
    });
  }

  private static boolean isSubPackage(String[] filters, String filter) {
    for (String supPackageFilter : filters) {
      if (filter.startsWith(supPackageFilter + ".")) {
        return true;
      }
    }
    return false;
  }

  public final @NotNull List<PsiClass> getCurrentSuiteClasses(final Project project) {
    final List<PsiClass> classes = new ArrayList<>();
    final String[] classNames = getFilteredClassNames();
    if (classNames.length > 0) {
      final DumbService dumbService = DumbService.getInstance(project);
      for (final String className : classNames) {
        ThrowableComputable<PsiClass, RuntimeException> computable = () -> {
          GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
          RunConfigurationBase<?> configuration = getConfiguration();
          if (configuration instanceof ModuleBasedConfiguration) {
            Module module = ((ModuleBasedConfiguration<?,?>)configuration).getConfigurationModule().getModule();
            if (module != null) {
              searchScope = GlobalSearchScope.moduleRuntimeScope(module, isTrackTestFolders());
            }
          }
          return JavaPsiFacade.getInstance(project).findClass(className.replace("$", "."), searchScope);
        };
        final PsiClass aClass = ReadAction.compute(() -> dumbService.computeWithAlternativeResolveEnabled(computable));
        if (aClass != null) {
          classes.add(aClass);
        }
      }
    }

    return classes;
  }
}
