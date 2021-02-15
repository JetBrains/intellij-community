// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.util.classFinder.ClassFinder;
import com.intellij.rt.coverage.util.classFinder.ClassPathEntry;
import com.intellij.util.lang.UrlClassLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

final class IdeaClassFinder extends ClassFinder {
  private final Project myProject;
  private final CoverageSuitesBundle myCurrentSuite;

  IdeaClassFinder(Project project, CoverageSuitesBundle currentSuite) {
    super(obtainPatternsFromSuite(currentSuite), new ArrayList());
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
  protected Collection getClassPathEntries() {
    final Collection entries = super.getClassPathEntries();
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
      if (extension != null) {
        final VirtualFile outputFile = extension.getCompilerOutputPath();
        if (outputFile != null) {
          entries.add(new ClassPathEntry(outputFile.getPath(), UrlClassLoader.build().files(Collections.singletonList(outputFile.toNioPath())).get()));
        }
        if (myCurrentSuite.isTrackTestFolders()) {
          final VirtualFile testOutput = extension.getCompilerOutputPathForTests();
          if (testOutput != null) {
            entries.add(new ClassPathEntry(testOutput.getPath(), UrlClassLoader.build().files(Collections.singletonList(testOutput.toNioPath())).get()));
          }
        }
      }
    }
    return entries;
  }
}
