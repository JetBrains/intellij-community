// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaCoverageSuite extends BaseCoverageSuite {
  private static final @NonNls String FILTER = "FILTER";
  private static final @NonNls String EXCLUDED_FILTER = "EXCLUDED_FILTER";
  private static final @NonNls String COVERAGE_RUNNER = "RUNNER";
  private final CoverageEngine myCoverageEngine;
  private String @Nullable [] myIncludeFilters;
  private String @Nullable [] myExcludePatterns;
  private boolean mySkipUnloadedClassesAnalysis;

  //read external only
  public JavaCoverageSuite(final @NotNull CoverageEngine coverageEngine) {
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
                           final @NotNull CoverageEngine coverageEngine,
                           final Project project) {
    super(name, project, coverageRunner, coverageDataFileProvider, lastCoverageTimeStamp);
    myCoverageEngine = coverageEngine;
    myBranchCoverage = branchCoverage;
    myTrackTestFolders = trackTestFolders;
    myCoverageByTestEnabled = coverageByTestEnabled;
    myIncludeFilters = includeFilters;
    myExcludePatterns = excludePatterns;

    if (coverageRunner instanceof JaCoCoCoverageRunner) {
      setSkipUnloadedClassesAnalysis(true);
    }
  }

  @Override
  public final @NotNull CoverageEngine getCoverageEngine() {
    return myCoverageEngine;
  }

  public final String @NotNull [] getFilteredPackageNames() {
    return getPackageNames(myIncludeFilters);
  }

  public final String @NotNull [] getExcludedPackageNames() {
    return getPackageNames(myExcludePatterns);
  }

  public final String @NotNull [] getFilteredClassNames() {
    return getClassNames(myIncludeFilters);
  }

  public final String @NotNull [] getExcludedClassNames() {
    return getClassNames(myExcludePatterns);
  }

  @VisibleForTesting
  public final String @Nullable [] getIncludeFilters() {
    return myIncludeFilters;
  }

  @VisibleForTesting
  public final void setIncludeFilters(String @Nullable [] filters) {
    myIncludeFilters = filters;
  }

  @VisibleForTesting
  public final String @Nullable [] getExcludePatterns() {
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

  public final boolean isClassFiltered(String classFQName) {
    if (matchesNames(classFQName, getExcludedClassNames())) return false;
    String packageName = StringUtil.getPackageName(classFQName);
    if (isSubPackageOf(packageName, getExcludedPackageNames())) return false;
    String[] filteredPackageNames = getFilteredPackageNames();
    if (isSubPackageOf(packageName, filteredPackageNames)) return true;
    if (matchesNames(classFQName, getFilteredClassNames())) return true;
    return filteredPackageNames.length == 0 && getFilteredClassNames().length == 0;
  }

  public final boolean isPackageFiltered(String packageFQName) {
    if (isSubPackageOf(packageFQName, getExcludedPackageNames())) return false;
    final String[] filteredPackageNames = getFilteredPackageNames();
    if (isSubPackageOf(packageFQName, getFilteredPackageNames())) return true;
    return filteredPackageNames.length == 0 && getFilteredClassNames().length == 0;
  }

  private static boolean isSubPackageOf(String packageFQName, String[] packages) {
    for (String packName : packages) {
      if (packName.isEmpty() || PsiNameHelper.isSubpackageOf(packageFQName, packName)) {
        return true;
      }
    }
    return false;
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

  public final @NotNull List<PsiClass> getCurrentSuiteClasses(final Project project) {
    final List<PsiClass> classes = new ArrayList<>();
    final String[] classNames = getFilteredClassNames();
    if (classNames.length > 0) {
      final DumbService dumbService = DumbService.getInstance(project);
      for (final String className : classNames) {
        PsiClass aClass = ReadAction.compute(() -> dumbService.computeWithAlternativeResolveEnabled(() -> {
          GlobalSearchScope searchScope = getSearchScope(project);
          return JavaPsiFacade.getInstance(project).findClass(className.replace("$", "."), searchScope);
        }));
        if (aClass != null) {
          classes.add(aClass);
        }
      }
    }

    return classes;
  }

  @Override
  public final void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    // filters
    myIncludeFilters = readFilters(element, FILTER);
    myExcludePatterns = readFilters(element, EXCLUDED_FILTER);

    if (myRunner == null) {
      myRunner = CoverageRunner.getInstance(IDEACoverageRunner.class); //default
    }
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

  private static boolean matchesNames(String classFQName, String[] classPatterns) {
    for (String className : classPatterns) {
      if (className.equals(classFQName) || classFQName.startsWith(className) && classFQName.charAt(className.length()) == '$') {
        return true;
      }
    }
    return false;
  }

  private static boolean isSubPackage(String[] filters, String filter) {
    for (String supPackageFilter : filters) {
      if (filter.startsWith(supPackageFilter + ".")) {
        return true;
      }
    }
    return false;
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

  private static String @NotNull [] getClassNames(final String[] filters) {
    if (filters == null) return ArrayUtilRt.EMPTY_STRING_ARRAY;
    List<String> result = new ArrayList<>();
    for (String filter : filters) {
      if (!filter.equals("*") && !filter.endsWith(".*")) result.add(filter);
    }
    return ArrayUtilRt.toStringArray(result);
  }

  private static String[] readFilters(Element element, final String tagName) {
    final List<Element> children = element.getChildren(tagName);
    List<String> filters = new ArrayList<>();
    for (Element child : children) {
      filters.add(child.getValue());
    }
    return filters.isEmpty() ? null : ArrayUtilRt.toStringArray(filters);
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
}
