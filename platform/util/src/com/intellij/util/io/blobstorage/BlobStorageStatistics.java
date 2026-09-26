// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.blobstorage;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;

/**
 * Provides access to storage internal statistics.
 * If storage provides access to such a statistics -- it must implement this interface
 */
@ApiStatus.Internal
public interface BlobStorageStatistics {

  /** records currently alive (=allocated-relocated-deleted) */
  int liveRecordsCount() throws IOException;

  /** records allocated since storage creation (including deleted/relocated) */
  int recordsAllocated() throws IOException;

  /** records re-allocated -- i.e. recordId is accessible, but the access redirects to another (actual) record */
  int recordsRelocated() throws IOException;

  /** records deleted, i.e. un-accessible anymore */
  int recordsDeleted() throws IOException;


  /**
   * Total size (bytes) occupied by storage data.
   * Includes all the overhead of file/records header, alignment, etc.
   * Could be <= file size, if e.g. file is expanded in advance.
   */
  long sizeInBytes() throws IOException;

  /** Total size of all alive records' payload, i.e. 'useful size', without any overhead */
  long totalLiveRecordsPayloadBytes() throws IOException;

  /** Total size of all alive records' capacity */
  long totalLiveRecordsCapacityBytes() throws IOException;
}
