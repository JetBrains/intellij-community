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

/**
 * Describes the {@code <fileType ... />} tag of the {@code com.intellij.fileType} extension point &mdash;
 * the declarative way to tell the IDE that a {@link FileType} exists and which files it is associated with.
 *
 * <p>
 * A tag is used in one of two ways:
 * <ul>
 *   <li><b>Registering a new file type</b>: specify {@link #name} and {@link #implementationClass},
 *       plus any number of association attributes:
 *       <pre>{@code
 * <fileType name="Python" language="Python" extensions="py;pyw" hashBangs="python"
 *           implementationClass="com.jetbrains.python.PythonFileType" fieldName="INSTANCE"/>
 *       }</pre>
 *   </li>
 *   <li><b>Adding associations to an already registered file type</b> (typically one owned by another plugin):
 *       specify {@link #name} and the association attributes only, and omit {@link #implementationClass}:
 *       <pre>{@code
 * <fileType name="XML" extensions="myxml"/>
 *       }</pre>
 *       The referenced file type must be registered by some other {@code <fileType>} tag; the order of the tags does not matter.
 *   </li>
 * </ul>
 *
 * <p>
 * All the association attributes are matched against the <em>file name</em> only. If deciding the file type requires more than
 * that &mdash; the full path, the surrounding files, or some other condition &mdash;
 * see {@link com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile}. However, this class is last resort, see its doc for details.
 *
 * <p>
 * On instantiation, the platform validates the bean against the actual {@link FileType} instance and reports a
 * {@link com.intellij.diagnostic.PluginException} on mismatch: {@link #name} must equal {@link FileType#getName()},
 * and {@link #language} must equal the language of a {@link LanguageFileType}. Registering two file types under the same
 * {@link #name} is an error as well.
 *
 * @see FileType
 * @see com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
 */
public final class FileTypeBean implements PluginAware {
  private final Collection<FileNameMatcher> myMatchers = new HashSet<>();

  private PluginDescriptor myPluginDescriptor;

  /**
   * Name of the class implementing the file type (must be a subclass of {@link FileType}).
   * <p>
   * Omit it if this declaration only adds associations to a file type registered elsewhere; in that case, only {@link #name}
   * and the association attributes must be specified.
   * <p>
   * The class is loaded lazily, on the first request for this file type &mdash; not on startup.
   */
  @Attribute("implementationClass")
  public String implementationClass;

  /**
   * Name of the static field in the {@link #implementationClass} class holding the file type instance,
   * usually {@code "INSTANCE"}.
   * <p>
   * A file type must be a singleton, so specifying this is strongly recommended. If omitted, the platform falls back to the only
   * static field whose type is the implementation class itself, and if there is no such field (or more than one), it calls the
   * constructor instead &mdash; which may produce a second instance of a file type that also has a public {@code INSTANCE} field.
   */
  @Attribute("fieldName")
  public String fieldName;

  /**
   * Name of the file type, unique across all the file types registered in the IDE.
   * Must match the return value of {@link FileType#getName()}.
   * <p>
   * This is a stable id, not a user-visible string: it is what gets written into the settings, so it must not be changed
   * or localized. See {@link FileType#getDisplayName()} for the presentable name.
   */
  @Attribute("name") @RequiredElement public @NonNls String name;

  /**
   * Semicolon-separated list of extensions to be associated with the file type, e.g. {@code "py;pyw"}.
   * <p>
   * Extensions must not be prefixed with a {@code '.'} and must not contain wildcards (use {@link #patterns} for those).
   * Matching is case-insensitive.
   */
  @Attribute("extensions") public @NonNls String extensions;

  /**
   * Semicolon-separated list of exact, case-sensitive file names to be associated with the file type, e.g. {@code "Makefile"}.
   *
   * @see #fileNamesCaseInsensitive
   */
  @Attribute("fileNames") public @NonNls String fileNames;

  /**
   * Semicolon-separated list of patterns (strings containing '?' and '*' characters) to be associated with the file type,
   * e.g. {@code "*.blade.php"}.
   * <p>
   * A pattern is matched against the file name, not against the path, so it cannot contain a {@code '/'}.
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
   * <p>
   * Conversely, it must be left unset for a {@linkplain LanguageFileType#isSecondary() secondary} language file type.
   * A mismatch is reported as an error when the file type is instantiated.
   */
  @Attribute("language")
  public String language;

  /**
   * Semicolon-separated list of hash bang patterns to be associated with the file type, e.g. {@code "python"} for {@code #!/usr/bin/python}.
   * <p>
   * A file matches if its first line starts with {@code "#!"} and contains the pattern as a substring.
   * <p>
   * Unlike the other attributes, this one looks at the file content, and so it is consulted only during content-based detection:
   * after all the name-based associations and all the {@link com.intellij.openapi.fileTypes.FileTypeRegistry.FileTypeDetector}s
   * have failed to recognize the file.
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