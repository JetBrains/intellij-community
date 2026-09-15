// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/// DB files:
/// ```
/// database.meta     // db header and metadata/schema
/// chunk.0000001.dat // chunks are fixed-size
/// chunk.0000002.dat // contain variable-sized blocks of stored data
/// chunk.0000003.dat
/// ```
///
/// Binary layouts:
/// ```
/// database.meta (=append-only log of DB schema changes) {
///   DatabaseHeaderLayout {            // first record only
///     magic       : int32
///     formatMajor : int16             // incompactible format change
///     formatMinor : int16             // potentially compatible format change
///     databaseId  : int64
///     chunkSize   : int32
///   }
///
///   CatalogChangeHeaderLayout {      // all the records after DatabaseHeader
///     type    : int16                 // =ChangeType
///     version : int16                 // version of change-type-specific payload binary format
///
///     change-type-specific payload {
///       ChunkChangePayloadLayout {    // CHUNK_CREATE | CHUNK_SEAL | CHUNK_RETIRE
///         chunkId:               int32
///       }
///       StoreDropPayloadLayout   {    // STORE_DROP
///         storeId:               int32
///       }
///       StoreCreatePayloadLayout {    // STORE_CREATE
///         storeId:               int32
///         dataVersion:           int32
///         storeMetadataVersion:  int32
///         nameLength:            int32
///         metadataLength:        int32
///         name:                  byte[nameLength]      // utf8 bytes
///         storeMetadata:         byte[metadataLength]  // arbitrary store-specific bytes
///       }
///       StoreMetadataPayloadLayout {  // STORE_METADATA_UPDATE
///         storeId:               int32
///         storeMetadataVersion:  int32
///         metadataLength:        int32
///         storeMetadata:         byte[metadataLength]  // arbitrary store-specific bytes
///       }
///     }
///   }
/// }
/// ```
/// Chunks files with data blocks:
/// ```
/// 'chunk_<nnnn>.dat' {
///   ChunkHeaderLayout {                   // file-header
///     magic:                      int32   // file-type id
///     chunkId:                    int32
///     databaseId:                 int64   // backref to 'database.meta'
///     committedTail:              int64
///     allocatedTail:              int64
///     chunkState:                 int32   // ChunkState{ ACTIVE | SEALED | RETIRED }
///
///     reserved:                   int8[28]
///   }
///
///   BlockHeaderLayout {                   // all subsequent records in the chunk
///     headerVersion:              int8
///     role:                       int8    // custom store tag (DB doesn't interpret it)
///     [padding]:                  int8[2]
///     storeId:                    int32   // store this block belongs to
///     blockId:                    int32   // logical ID: kept as block is relocated on compaction
///     blockLength:                int32
///     state:                      int32   // lifecycle { ALLOCATED | ACTIVE | SEALED | RETIRED }
///
///     blockData:                  byte[blockLength]   //custom store-specific payload
///   }
/// }
/// ```
@Internal
package com.intellij.platform.util.io.storages.database.impl.layout;

import org.jetbrains.annotations.ApiStatus.Internal;
