// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.exceptions


internal abstract class MissingMetadataException(message: String): Exception(message)


internal class MissingTypeMetadataException(fqName: String): MissingMetadataException("Metadata for the $fqName was not collected. ${runGeneratorMessage(fqName)}")

internal class MissingTypeMetadataHashException(fqName: String): MissingMetadataException("Metadata hash for the $fqName was not collected. ${runGeneratorMessage(fqName)}")


internal class MissingMetadataStorage(metadataStorageFqn: String, typeFqn: String):
  Exception("Metadata storage $metadataStorageFqn was not found. ${runGeneratorMessage(typeFqn)}")

private fun runGeneratorMessage(typeFqn: String): String = "Please run the generator for the class $typeFqn"