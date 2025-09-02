// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.tooling.util.StringUtils;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public class AuxiliaryConfigurationArtifacts {

  private static final Pattern PUNCTUATION_IN_SUFFIX_PATTERN = Pattern.compile("[\\p{Punct}\\s]+$");

  private final @NotNull Map<ComponentIdentifier, Set<File>> sources;
  private final @NotNull Map<ComponentIdentifier, Set<File>> javadocs;

  public AuxiliaryConfigurationArtifacts(@NotNull Map<ComponentIdentifier, Set<File>> sources,
                                         @NotNull Map<ComponentIdentifier, Set<File>> javadocs
  ) {
    this.sources = sources;
    this.javadocs = javadocs;
  }

  public @Nullable File getJavadoc(@NotNull ComponentIdentifier identifier,
                                   @NotNull File artifactFile
  ) {
    Set<File> files = javadocs.get(identifier);
    if (files == null) {
      return null;
    }
    return chooseAuxiliaryArtifactFile(artifactFile, files);
  }

  public @Nullable File getSources(@NotNull ComponentIdentifier identifier,
                                   @NotNull File artifactFile
  ) {
    Set<File> files = sources.get(identifier);
    if (files == null) {
      return null;
    }
    return chooseAuxiliaryArtifactFile(artifactFile, files);
  }

  /**
   * If there are multiple auxiliary artifacts for the same `ComponentIdentifier`, we have to choose the "best match" based on file names.
   * For context, see IDEA-332969
   * 1. Find the common suffix of every auxiliary artifact (e.g. "-sources.jar" or ".src.jar") and ignore it going forward
   * 2. Find the common suffix of the main artifact with the auxiliary artifacts (e.g. ".jar") and ignore it going forward
   * 3. Filter the auxiliary artifacts, keeping only those that have the longest common prefix with the main artifact (not counting any
   * punctuation or whitespace at the end of the common prefix)
   * 4. Deterministically choose from the remaining auxiliary artifacts, preferring the shortest overall file name (the longer ones likely
   * belong to some different main artifact that also has a longer file name)
   *
   * @param main        path to the dependency Jar file
   * @param auxiliaries set of artifacts associated with this library
   * @return best match, null otherwise
   */
  @VisibleForTesting
  public static @Nullable File chooseAuxiliaryArtifactFile(@NotNull File main, @NotNull Set<File> auxiliaries) {
    Iterator<File> auxiliariesIterator = auxiliaries.iterator();
    if (!auxiliariesIterator.hasNext()) {
      return null;
    }

    File firstAuxiliary = auxiliariesIterator.next();
    if (!auxiliariesIterator.hasNext()) {
      return firstAuxiliary;
    }

    String mainName = main.getName();
    String firstAuxiliaryName = firstAuxiliary.getName();

    int commonSuffixOfAuxiliaries = firstAuxiliaryName.length();
    do {
      File nextAuxiliary = auxiliariesIterator.next();
      int commonSuffix = StringUtils.commonSuffixLength(firstAuxiliaryName, nextAuxiliary.getName());
      if (commonSuffix < commonSuffixOfAuxiliaries) {
        commonSuffixOfAuxiliaries = commonSuffix;
      }
    }
    while (auxiliariesIterator.hasNext());

    int commonSuffixOfMainAndAuxiliaries = Math.min(
      commonSuffixOfAuxiliaries,
      StringUtils.commonSuffixLength(mainName, firstAuxiliaryName)
    );
    String mainSuffixlessName = mainName.substring(0, mainName.length() - commonSuffixOfMainAndAuxiliaries);

    int commonPrefixOfMainAndShortlistedAuxiliaries = 0;
    TreeMap<String, File> shortlistedAuxiliariesBySuffixlessName =
      new TreeMap<>(Comparator.comparingInt(String::length).thenComparing(String::compareTo));
    for (File auxiliary : auxiliaries) {
      String auxiliaryName = auxiliary.getName();
      String auxiliarySuffixlessName = auxiliaryName.substring(0, auxiliaryName.length() - commonSuffixOfAuxiliaries);
      int commonPrefixNaive = StringUtils.commonPrefixLength(mainSuffixlessName, auxiliarySuffixlessName);
      Matcher commonPrefixExcessMatcher = PUNCTUATION_IN_SUFFIX_PATTERN.matcher(auxiliarySuffixlessName).region(0, commonPrefixNaive);
      int commonPrefix = commonPrefixExcessMatcher.find() ? commonPrefixExcessMatcher.start() : commonPrefixNaive;
      if (commonPrefix >= commonPrefixOfMainAndShortlistedAuxiliaries) {
        if (commonPrefix > commonPrefixOfMainAndShortlistedAuxiliaries) {
          commonPrefixOfMainAndShortlistedAuxiliaries = commonPrefix;
          shortlistedAuxiliariesBySuffixlessName.clear();
        }
        shortlistedAuxiliariesBySuffixlessName.put(auxiliarySuffixlessName, auxiliary);
      }
    }

    return shortlistedAuxiliariesBySuffixlessName.firstEntry().getValue();
  }
}
