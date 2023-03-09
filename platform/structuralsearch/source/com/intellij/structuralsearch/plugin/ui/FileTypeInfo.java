// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.structuralsearch.PatternContext;
import com.intellij.structuralsearch.SSRBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Pavel.Dolgov
 */
class FileTypeInfo {
  public static final FileTypeInfo[] EMPTY_ARRAY = new FileTypeInfo[0];

  private final LanguageFileType myFileType;
  private final Language myDialect;
  private final PatternContext myContext;
  private final boolean myNested;
  private final String myDescription;

  FileTypeInfo(@NotNull LanguageFileType fileType, @NotNull Language dialect, @Nullable PatternContext context, boolean nested) {
    myFileType = fileType;
    myDialect = dialect;
    myContext = context;
    myNested = nested;
    myDescription = fileType.getDescription();
  }

  @NotNull
  public LanguageFileType getFileType() {
    return myFileType;
  }

  @Nullable
  public Language getDialect() {
    return myDialect;
  }

  @Nullable
  public PatternContext getContext() {
    return myContext;
  }

  public @NlsSafe @NotNull String getText() {
    if (myNested) {
      if (myDialect != null && myDialect != myFileType.getLanguage()) {
        return myDialect.getDisplayName();
      }
      if (myContext != null) {
        return SSRBundle.message("file.type.pattern.context", myDescription, myContext.getDisplayName());
      }
    }
    return myDescription;
  }

  public boolean isNested() {
    return myNested;
  }

  public boolean isEqualTo(@NotNull LanguageFileType fileType, @Nullable Language dialect, @Nullable PatternContext context) {
    return (myFileType == fileType)
           && (dialect == null || myDialect == dialect)
           && (context == null || myContext == context);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileTypeInfo)) return false;
    final FileTypeInfo info = (FileTypeInfo)o;
    return myFileType == info.myFileType
           && myDialect == info.myDialect
           && myContext == info.myContext;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myFileType, myDialect, myContext);
  }

  @Override
  public String toString() {
    return getText();
  }
}
