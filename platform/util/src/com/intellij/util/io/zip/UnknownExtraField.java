/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.intellij.util.io.zip;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

class UnrecognizedExtraField implements JBZipExtraField {
  private final @NotNull ZipShort myHeaderId;

  private byte[] myLocalData;
  private byte[] myCentralData;

  UnrecognizedExtraField(@NotNull ZipShort headerId) {
    myHeaderId = headerId;
  }

  @Override
  public @NotNull ZipShort getHeaderId() {
    return myHeaderId;
  }

  public void setLocalFileDataData(final byte[] data) {
    myLocalData = ArrayUtil.copyOf(data);
  }

  @Override
  public @NotNull ZipShort getLocalFileDataLength() {
    return new ZipShort(myLocalData != null ? myLocalData.length : 0);
  }

  @Override
  public byte @NotNull [] getLocalFileDataData() {
    return ArrayUtil.copyOf(myLocalData);
  }

  public void setCentralDirectoryData(final byte[] data) {
    myCentralData = ArrayUtil.copyOf(data);
  }

  @Override
  public @NotNull ZipShort getCentralDirectoryLength() {
    if (myCentralData != null) {
      return new ZipShort(myCentralData.length);
    }
    return getLocalFileDataLength();
  }

  @Override
  public byte @NotNull [] getCentralDirectoryData() {
    if (myCentralData != null) {
      return ArrayUtil.copyOf(myCentralData);
    }
    return getLocalFileDataData();
  }

  @Override
  public void parseFromLocalFileData(byte @NotNull [] data,
                                     int offset,
                                     int length) {
    setLocalFileDataData(Arrays.copyOfRange(data, offset, offset + length));
  }

  @Override
  public void parseFromCentralDirectoryData(byte @NotNull [] data,
                                            int offset,
                                            int length) {
    final byte[] tmp = Arrays.copyOfRange(data, offset, offset + length);
    setCentralDirectoryData(tmp);
    if (myLocalData == null) {
      setLocalFileDataData(tmp);
    }
  }
}
