// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.exceptions


public abstract class MissingMetadataException(metadataType: String): Exception("Metadata for the $metadataType was not collected")


public class MissingTypeMetadataException(fqName: String): MissingMetadataException("type $fqName")


public class MissingMetadataStorage(metadataStorageFqn: String): Exception("Metadata storage $metadataStorageFqn was not found")