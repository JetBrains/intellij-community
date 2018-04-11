/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.gradle.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.pattern.PatternMatcherFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 * @since 7/10/2014
 */
public class GradleResourceFileFilter implements FileFilter {
  private final FilePattern myFilePattern;
  private final File myRoot;
  private final Spec<RelativePath> myFileFilterSpec;

  public GradleResourceFileFilter(@NotNull File rootFile, @NotNull FilePattern filePattern) {
    myFilePattern = filePattern;
    myRoot = rootFile;
    myFileFilterSpec = getAsSpec();
  }

  public boolean accept(@NotNull File file) {
    final String relPath = FileUtil.getRelativePath(myRoot, file);
    return relPath != null && isIncluded(relPath);
  }

  private boolean isIncluded(@NotNull String relativePath) {
    RelativePath path = new RelativePath(true, relativePath.split(Pattern.quote(File.separator)));
    return myFileFilterSpec.isSatisfiedBy(path);
  }

  private Spec<RelativePath> getAsSpec() {
    return Specs.intersect(getAsIncludeSpec(true), Specs.negate(getAsExcludeSpec(true)));
  }

  private Spec<RelativePath> getAsExcludeSpec(boolean caseSensitive) {
    Collection<String> allExcludes = new LinkedHashSet<>(myFilePattern.excludes);
    List<Spec<RelativePath>> matchers = new ArrayList<>();
    for (String exclude : allExcludes) {
      Spec<RelativePath> patternMatcher = PatternMatcherFactory.getPatternMatcher(false, caseSensitive, exclude);
      matchers.add(patternMatcher);
    }
    if (matchers.isEmpty()) {
      return Specs.satisfyNone();
    }
    return Specs.union(matchers);
  }

  private Spec<RelativePath> getAsIncludeSpec(boolean caseSensitive) {
    List<Spec<RelativePath>> matchers = new ArrayList<>();
    for (String include : myFilePattern.includes) {
      Spec<RelativePath> patternMatcher = PatternMatcherFactory.getPatternMatcher(true, caseSensitive, include);
      matchers.add(patternMatcher);
    }
    return Specs.union(matchers);
  }
}