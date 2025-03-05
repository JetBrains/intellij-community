// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class LocalTestDiscoveryProducer implements TestDiscoveryProducer {
  @Override
  public @NotNull MultiMap<String, String> getDiscoveredTests(@NotNull Project project,
                                                              @NotNull List<? extends Couple<String>> classesAndMethods,
                                                              byte frameworkId) {
    MultiMap<String, String> result = new MultiMap<>();
    TestDiscoveryIndex instance = TestDiscoveryIndex.getInstance(project);
    classesAndMethods.forEach(couple -> result.putAllValues(couple.second == null ?
                                                            instance.getTestsByClassName(couple.first, frameworkId) :
                                                            instance.getTestsByMethodName(couple.first, couple.second, frameworkId)));
    return result;
  }

  @Override
  public @NotNull MultiMap<String, String> getDiscoveredTestsForFiles(@NotNull Project project, @NotNull List<String> paths, byte frameworkId) {
    MultiMap<String, String> result = new MultiMap<>();
    TestDiscoveryIndex instance = TestDiscoveryIndex.getInstance(project);
    for (String path : paths) {
      result.putAllValues(instance.getTestsByFile(path, frameworkId));
    }
    return result;
  }

  @Override
  public @NotNull List<String> getAffectedFilePaths(@NotNull Project project, @NotNull List<? extends Couple<String>> testFqns, byte frameworkId) {
    TestDiscoveryIndex instance = TestDiscoveryIndex.getInstance(project);
    Set<String> result = new HashSet<>();
    for (Couple<String> test : testFqns) {
      result.addAll(instance.getAffectedFiles(test, frameworkId));
    }
    return new ArrayList<>(result);
  }

  @Override
  public @NotNull List<String> getAffectedFilePathsByClassName(@NotNull Project project, @NotNull String testClassName, byte frameworkId) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull List<String> getFilesWithoutTests(@NotNull Project project, @NotNull Collection<String> paths) {
    return Collections.emptyList(); // todo[batkovich]: implement, please
  }

  @Override
  public boolean isRemote() {
    return false;
  }
}
