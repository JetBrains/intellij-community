// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public final class FileTypeBean implements PluginAware {
  private final Collection<FileNameMatcher> myMatchers = new HashSet<>();

  private PluginDescriptor myPluginDescriptor;

  /**
   * Name of the class implementing the file type (must be a subclass of {@link FileType}). This can be omitted
   * if the fileType declaration is used to add extensions to an existing file type (in this case, only 'name'
   * and 'extensions' attributes must be specified).
   */
  @Attribute("implementationClass")
  public String implementationClass;

  /**
   * Name of the public static field in the 'implementationClass' class containing the file type instance.
   */
  @Attribute("fieldName")
  public String fieldName;

  /**
   * Name of the file type. Needs to match the return value of {@link FileType#getName()}.
   */
  @Attribute("name") @RequiredElement public @NonNls String name;

  /**
   * Semicolon-separated list of extensions to be associated with the file type. Extensions
   * must not be prefixed with a `.`.
   */
  @Attribute("extensions") public @NonNls String extensions;

  /**
   * Semicolon-separated list of exact file names to be associated with the file type.
   *
   * @see #fileNamesCaseInsensitive
   */
  @Attribute("fileNames") public @NonNls String fileNames;

  /**
   * Semicolon-separated list of patterns (strings containing '?' and '*' characters) to be associated with the file type.
   */
  @Attribute("patterns") public @NonNls String patterns;

  /**
   * Semicolon-separated list of exact file names (case-insensitive) to be associated with the file type.
   *
   * @see #fileNames
   */
  @Attribute("fileNamesCaseInsensitive") public @NonNls String fileNamesCaseInsensitive;

  /**
   * For file types that extend {@link LanguageFileType} and are the primary file type for the corresponding language, this must be set
   * to the ID of the language returned by {@link LanguageFileType#getLanguage()}.
   */
  @Attribute("language")
  public String language;

  /**
   * Semicolon-separated list of hash bang patterns to be associated with the file type.
   */
  @Attribute("hashBangs") public @NonNls String hashBangs;

  @ApiStatus.Internal
  void addMatchers(@NotNull List<? extends FileNameMatcher> matchers) {
    myMatchers.addAll(matchers);
  }

  @ApiStatus.Internal
  @NotNull List<FileNameMatcher> getMatchers() {
    return new ArrayList<>(myMatchers);
  }

  @Transient
  public @NotNull PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public @NotNull PluginId getPluginId() {
    return myPluginDescriptor.getPluginId();
  }
}