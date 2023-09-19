// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.workspace.storage.impl.ClassToIntConverter
import com.intellij.platform.workspace.storage.metadata.resolver.TypeMetadataResolver
import com.intellij.platform.workspace.storage.metadata.utils.MetadataTypesFqnComparator
import com.intellij.platform.workspace.storage.tests.ApplicationRuleService
import com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestClassToIntConverter
import com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestMetadataTypesFqnComparator
import com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestTypeMetadataResolver
import com.intellij.testFramework.registerServiceInstance
import org.junit.Before

abstract class MetadataSerializationTest: ApplicationRuleService() {

  @Before
  fun setUp() {
    ApplicationManager.getApplication()
      .registerServiceInstance(ClassToIntConverter::class.java, TestClassToIntConverter())
    ApplicationManager.getApplication()
      .registerServiceInstance(TypeMetadataResolver::class.java, TestTypeMetadataResolver())
    ApplicationManager.getApplication()
      .registerServiceInstance(MetadataTypesFqnComparator::class.java, TestMetadataTypesFqnComparator())
  }
}