// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.PatternContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author Pavel.Dolgov
 */
public class FileTypeInfo {
  public static final FileTypeInfo[] EMPTY_ARRAY = new FileTypeInfo[0];

  /** @see com.intellij.openapi.fileTypes.impl.FileTypeRenderer */
  private static final Pattern CLEANUP = Pattern.compile("(?i)\\s+file(?:s)?$");

  private final LanguageFileType myFileType;
  private final Language myDialect;
  private final PatternContext myContext;
  private final boolean myNested;
  private final String myDescription;

  public FileTypeInfo(@NotNull LanguageFileType fileType, @NotNull Language dialect, @Nullable PatternContext context, boolean nested) {
    myFileType = fileType;
    myDialect = dialect;
    myContext = context;
    myNested = nested;
    myDescription = getDescription(fileType);
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

  @NotNull
  public String getText() {
    if (myNested) {
      if (myDialect != null && myDialect != myFileType.getLanguage()) {
        return myDialect.getDisplayName();
      }
      if (myContext != null) {
        return myDescription + " - " + myContext.getDisplayName();
      }
    }
    return myDescription;
  }

  @NotNull
  public String getSearchText() {
    return (myDialect != null) ? myDialect.getDisplayName() : myFileType.getName();
  }

  public boolean isNested() {
    return myNested;
  }

  public boolean isEqualTo(@NotNull LanguageFileType fileType, @Nullable Language dialect, @Nullable PatternContext context) {
    return (myFileType == fileType)
           && (dialect == null || myDialect == dialect)
           && (context == null || myContext == context);
  }

  @NotNull
  private static String getDescription(@NotNull LanguageFileType fileType) {
    final String description = fileType.getDescription();
    return StringUtil.capitalizeWords(CLEANUP.matcher(description).replaceAll(""), true);
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
