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
  private final String myDescription;

  public FileTypeInfo(@NotNull FileType fileType, @Nullable Language dialect, boolean duplicated) {
    myFileType = fileType;
    myDialect = dialect;
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

  @NotNull
  public String getText() {
    if (myDialect != null) {
      return myDialect.getDisplayName();
    }
    return myFileType.getName();
  }

  @NotNull
  public String getFullText() {
    if (myDialect == null) {
      return myDescription;
    }
    return myDescription + " - " + myDialect.getDisplayName();
  }

  public boolean isDialect() {
    return myDialect != null;
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
           Objects.equals(myDialect, info.myDialect);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myFileType, myDialect);
  }
}
