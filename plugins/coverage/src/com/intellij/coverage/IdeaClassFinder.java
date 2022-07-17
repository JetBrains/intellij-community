// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.util.classFinder.ClassFinder;
import com.intellij.rt.coverage.util.classFinder.ClassPathEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

final class IdeaClassFinder extends ClassFinder {
  private final Project myProject;
  private final CoverageSuitesBundle myCurrentSuite;

  IdeaClassFinder(Project project, CoverageSuitesBundle currentSuite) {
    super(obtainPatternsFromSuite(currentSuite), new ArrayList<>());
    myProject = project;
    myCurrentSuite = currentSuite;
  }

  private static List<Pattern> obtainPatternsFromSuite(CoverageSuitesBundle currentSuiteBundle) {
    final List<Pattern> includePatterns = new ArrayList<>();
    for (CoverageSuite currentSuite : currentSuiteBundle.getSuites()) {
      for (String pattern : ((JavaCoverageSuite)currentSuite).getFilteredPackageNames()) {
        includePatterns.add(Pattern.compile(pattern + ".*"));
      }

      for (String pattern : ((JavaCoverageSuite)currentSuite).getFilteredClassNames()) {
        includePatterns.add(Pattern.compile(pattern));
      }
    }
    return includePatterns;
  }

  @Override
  protected Collection<ClassPathEntry> getClassPathEntries() {
    final Collection<ClassPathEntry> entries = new HashSet<>();
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    final CoverageDataManager coverageManager = CoverageDataManager.getInstance(myProject);
    for (Module module : modules) {
      final VirtualFile[] roots = JavaCoverageClassesEnumerator.getRoots(coverageManager, module, myCurrentSuite.isTrackTestFolders());
      if (roots == null) continue;
      for (VirtualFile root : roots) {
        entries.add(new ClassPathEntry(root.getPath()));
      }
    }
    return entries;
  }
}
