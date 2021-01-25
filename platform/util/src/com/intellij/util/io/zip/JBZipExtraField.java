// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.zip;

import org.jetbrains.annotations.NotNull;

import java.util.zip.ZipException;

public interface JBZipExtraField {
  /**
   * The Header-ID.
   *
   * @return The HeaderId value
   */
  @NotNull
  ZipShort getHeaderId();

  /**
   * Length of the extra field in the local file data - without
   * Header-ID or length specifier.
   * @return the length of the field in the local file data
   */
  @NotNull
  ZipShort getLocalFileDataLength();

  /**
   * Length of the extra field in the central directory - without
   * Header-ID or length specifier.
   * @return the length of the field in the central directory
   */
  @NotNull
  ZipShort getCentralDirectoryLength();

  /**
   * The actual data to put into local file data - without Header-ID
   * or length specifier.
   * @return the data
   */
  byte @NotNull [] getLocalFileDataData();

  /**
   * The actual data to put into central directory - without Header-ID or
   * length specifier.
   * @return the data
   */
  byte @NotNull [] getCentralDirectoryData();

  /**
   * Populate data from this array as if it was in local file data.
   */
  void parseFromLocalFileData(byte @NotNull [] buffer, int offset, int length)
    throws ZipException;

  /**
   * Populate data from this array as if it was in central directory data.
   */
  void parseFromCentralDirectoryData(byte @NotNull [] buffer, int offset, int length)
    throws ZipException;
}
