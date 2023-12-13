// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeExtension;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.util.KeyedLazyInstance;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.maddyhome.idea.copyright.options.Options Options for storing language settings
 */
public final class CopyrightUpdaters extends FileTypeExtension<UpdateCopyrightsProvider> {
  public static final ExtensionPointName<KeyedLazyInstance<UpdateCopyrightsProvider>> EP_NAME =
    new ExtensionPointName<>("com.intellij.copyright.updater");
  public final static CopyrightUpdaters INSTANCE = new CopyrightUpdaters();

  private CopyrightUpdaters() {
    super(EP_NAME);
  }

  @Override
  public UpdateCopyrightsProvider forFileType(@NotNull FileType type) {
    FileType acceptable = getRegisteredFileTypeFromLanguageHierarchy(type);
    return acceptable == null ? null : super.forFileType(acceptable);
  }

  @Nullable
  public FileType getRegisteredFileTypeFromLanguageHierarchy(@NotNull FileType type) {
    if (super.forFileType(type) != null) return type;

    while (type instanceof LanguageFileType lft) {
      Language language = lft.getLanguage();
      if (!lft.isSecondary()) {
        language = language.getBaseLanguage();
      }
      if (language == null) {
        break;
      }

      FileType baseFileType = FileTypeRegistry.getInstance().findFileTypeByLanguage(language);
      if (baseFileType == null) break;
      if (super.forFileType(baseFileType) != null) return baseFileType;

      type = baseFileType;
    }

    return null;
  }
}
