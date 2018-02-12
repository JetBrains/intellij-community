/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.coverage;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class JavaCoverageSuite extends BaseCoverageSuite {
  private static final Logger LOG = Logger.getInstance(JavaCoverageSuite.class.getName());

  private String[] myFilters;
  private String mySuiteToMerge;

  @NonNls
  private static final String FILTER = "FILTER";
  private static final String EXCLUDED_FILTER = "EXCLUDED_FILTER";
  @NonNls
  private static final String MERGE_SUITE = "MERGE_SUITE";
  @NonNls
  private static final String COVERAGE_RUNNER = "RUNNER";
  private String[] myExcludePatterns;
  private final CoverageEngine myCoverageEngine;

  //read external only
  public JavaCoverageSuite(@NotNull final JavaCoverageEngine coverageSupportProvider) {
    super();
    myCoverageEngine = coverageSupportProvider;
  }

  public JavaCoverageSuite(final String name,
                           final CoverageFileProvider coverageDataFileProvider,
                           final String[] filters,
                           final String[] excludePatterns,
                           final long lastCoverageTimeStamp,
                           final boolean coverageByTestEnabled,
                           final boolean tracingEnabled,
                           final boolean trackTestFolders,
                           final CoverageRunner coverageRunner,
                           @NotNull final JavaCoverageEngine coverageSupportProvider,
                           final Project project) {
    super(name, coverageDataFileProvider, lastCoverageTimeStamp, coverageByTestEnabled,
          tracingEnabled, trackTestFolders,
          coverageRunner != null ? coverageRunner : CoverageRunner.getInstance(IDEACoverageRunner.class), project);

    myFilters = filters;
    myExcludePatterns = excludePatterns;
    myCoverageEngine = coverageSupportProvider;
  }

  @NotNull
  public String[] getFilteredPackageNames() {
    return getPackageNames(myFilters);
  }

  @NotNull
  public String[] getExcludedPackageNames() {
    return getPackageNames(myExcludePatterns);
  }

  private static String[] getPackageNames(String[] filters) {
    if (filters == null || filters.length == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    List<String> result = new ArrayList<>();
    for (String filter : filters) {
      if (filter.equals("*")) {
        result.add(""); //default package
      }
      else if (filter.endsWith(".*")) result.add(filter.substring(0, filter.length() - 2));
    }
    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  public String[] getFilteredClassNames() {
    return getClassNames(myFilters);
  }

  @NotNull
  public String[] getExcludedClassNames() {
    return getClassNames(myExcludePatterns);
  }

  @NotNull
  private static String[] getClassNames(final String[] filters) {
    if (filters == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    List<String> result = new ArrayList<>();
    for (String filter : filters) {
      if (!filter.equals("*") && !filter.endsWith(".*")) result.add(filter);
    }
    return ArrayUtil.toStringArray(result);
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);

    // filters
    myFilters = readFilters(element, FILTER);
    myExcludePatterns = readFilters(element, EXCLUDED_FILTER);

    // suite to merge
    mySuiteToMerge = element.getAttributeValue(MERGE_SUITE);

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
    return filters.isEmpty() ? null : ArrayUtil.toStringArray(filters);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    if (mySuiteToMerge != null) {
      element.setAttribute(MERGE_SUITE, mySuiteToMerge);
    }
    writeFilters(element, myFilters, FILTER);
    writeFilters(element, myExcludePatterns, EXCLUDED_FILTER);
    final CoverageRunner coverageRunner = getRunner();
    element.setAttribute(COVERAGE_RUNNER, coverageRunner != null ? coverageRunner.getId() : "emma");
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

  @Nullable
  public ProjectData getCoverageData(final CoverageDataManager coverageDataManager) {
    final ProjectData data = getCoverageData();
    if (data != null) return data;
    ProjectData map = loadProjectInfo();
    if (mySuiteToMerge != null) {
      JavaCoverageSuite toMerge = null;
      final CoverageSuite[] suites = coverageDataManager.getSuites();
      for (CoverageSuite suite : suites) {
        if (Comparing.strEqual(suite.getPresentableName(), mySuiteToMerge)) {
          if (!Comparing.strEqual(((JavaCoverageSuite)suite).getSuiteToMerge(), getPresentableName())) {
            toMerge = (JavaCoverageSuite)suite;
          }
          break;
        }
      }
      if (toMerge != null) {
        final ProjectData projectInfo = toMerge.getCoverageData(coverageDataManager);
        if (map != null) {
          map.merge(projectInfo);
        } else {
          map = projectInfo;
        }
      }
    }
    setCoverageData(map);
    return map;
  }

  @NotNull
  public CoverageEngine getCoverageEngine() {
    return myCoverageEngine;
  }

  @Nullable
  public String getSuiteToMerge() {
    return mySuiteToMerge;
  }

  public boolean isClassFiltered(final String classFQName) {
    return isClassFiltered(classFQName, getFilteredClassNames());
  }

  public boolean isClassFiltered(final String classFQName,
                                 final String[] classPatterns) {
    for (final String className : classPatterns) {
      if (className.equals(classFQName) || classFQName.startsWith(className) && classFQName.charAt(className.length()) == '$') {
        return true;
      }
    }
    return false;
  }

  public boolean isPackageFiltered(final String packageFQName) {
    for (String name : getExcludedPackageNames()) {
      if (packageFQName.equals(name) || packageFQName.startsWith(name + ".")) return false;
    }
    final String[] filteredPackageNames = getFilteredPackageNames();
    for (final String packName : filteredPackageNames) {
      if (packName.equals(packageFQName) || packageFQName.startsWith(packName) && packageFQName.charAt(packName.length()) == '.') {
        return true;
      }
    }
    return filteredPackageNames.length == 0 && getFilteredClassNames().length == 0;
  }

  public @NotNull List<PsiPackage> getCurrentSuitePackages(final Project project) {
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

  public @NotNull List<PsiClass> getCurrentSuiteClasses(final Project project) {
    final List<PsiClass> classes = new ArrayList<>();
    final String[] classNames = getFilteredClassNames();
    if (classNames.length > 0) {
      for (final String className : classNames) {
        final PsiClass aClass =
          ReadAction.compute(() -> {
            final DumbService dumbService = DumbService.getInstance(project);
            dumbService.setAlternativeResolveEnabled(true);
            try {
              return JavaPsiFacade.getInstance(project).findClass(className.replace("$", "."), GlobalSearchScope.allScope(project));
            }
            finally {
              dumbService.setAlternativeResolveEnabled(false);
            }
          });
        if (aClass != null) {
          classes.add(aClass);
        }
      }
    }

    return classes;
  }
}
