// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization

import com.intellij.platform.workspace.storage.impl.ClassToIntConverter
import com.intellij.platform.workspace.storage.metadata.resolver.TypeMetadataResolver
import com.intellij.platform.workspace.storage.metadata.utils.MetadataTypesFqnComparator
import com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestClassToIntConverter
import com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestMetadataTypesFqnComparator
import com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestTypeMetadataResolver
import org.junit.After
import org.junit.Before

abstract class MetadataSerializationTest {
  private val classToIntConverter = ClassToIntConverter.getInstance()
  private val typeMetadataResolver = TypeMetadataResolver.getInstance()
  private val metadataTypesFqnComparator = MetadataTypesFqnComparator.getInstance()

  @Before
  fun setUp() {
    ClassToIntConverter.replaceClassToIntConverter(TestClassToIntConverter(classToIntConverter))
    TypeMetadataResolver.replaceTypeMetadataResolver(TestTypeMetadataResolver(typeMetadataResolver))
    MetadataTypesFqnComparator.replaceMetadataTypesFqnComparator(TestMetadataTypesFqnComparator)
  }

  @After
  fun tearDown() {
    ClassToIntConverter.replaceClassToIntConverter(classToIntConverter)
    TypeMetadataResolver.replaceTypeMetadataResolver(typeMetadataResolver)
    MetadataTypesFqnComparator.replaceMetadataTypesFqnComparator(metadataTypesFqnComparator)
  }
}