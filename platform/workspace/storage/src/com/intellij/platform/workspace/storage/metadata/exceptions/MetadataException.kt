// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.exceptions


public abstract class MissingMetadataException(message: String): Exception(message)


public class MissingTypeMetadataException(fqName: String): MissingMetadataException("Metadata for the $fqName was not collected")

public class MissingTypeMetadataHashException(fqName: String): MissingMetadataException("Metadata hash for the $fqName was not collected")


public class MissingMetadataStorage(metadataStorageFqn: String): Exception("Metadata storage $metadataStorageFqn was not found")