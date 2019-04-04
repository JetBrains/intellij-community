// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
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

  private final FileType myFileType;
  private final Language myDialect;
  private final String myContext;
  private final boolean myEnabled;
  private final String myDescription;

  public FileTypeInfo(@NotNull FileType fileType,
                      @Nullable Language dialect,
                      @Nullable String context,
                      boolean enabled,
                      boolean duplicated) {
    myFileType = fileType;
    myDialect = dialect;
    myContext = context;
    myEnabled = enabled;
    myDescription = getDescription(fileType, duplicated);
  }

  @NotNull
  public FileType getFileType() {
    return myFileType;
  }

  @Nullable
  public Language getDialect() {
    return myDialect;
  }

  @Nullable
  public String getContext() {
    return myContext;
  }

  @NotNull
  public String getText() {
    if (myDialect != null) {
      return myDialect.getDisplayName();
    }
    if (myContext != null) {
      return myContext + " Context";
    }
    return myFileType.getName();
  }

  @NotNull
  public String getSearchText() {
    if (myDialect != null) {
      return myDialect.getDisplayName();
    }
    return myFileType.getName();
  }

  @NotNull
  public String getFullText() {
    if (myDialect != null) {
      return myDescription + " - " + myDialect.getDisplayName();
    }
    if (myContext != null) {
      return myDescription + " - " + myContext + " Context";
    }
    return myDescription;
  }

  public boolean isNested() {
    return myDialect != null || myContext != null;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public boolean isEqualTo(@NotNull FileType fileType, @Nullable Language dialect, @Nullable String context) {
    return Objects.equals(myFileType, fileType) &&
           Objects.equals(myDialect, dialect) &&
           Objects.equals(myContext, context);
  }

  @NotNull
  private static String getDescription(@NotNull FileType fileType, boolean duplicated) {
    String description = fileType.getDescription();
    String trimmedDescription = StringUtil.capitalizeWords(CLEANUP.matcher(description).replaceAll(""), true);
    if (!duplicated) {
      return trimmedDescription;
    }
    return trimmedDescription + " (" + fileType.getName() + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FileTypeInfo)) return false;
    FileTypeInfo info = (FileTypeInfo)o;
    return Objects.equals(myFileType, info.myFileType) &&
           Objects.equals(myDialect, info.myDialect) &&
           Objects.equals(myContext, info.myContext);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myFileType, myDialect, myContext);
  }

  @Override
  public String toString() {
    return getFullText();
  }
}
