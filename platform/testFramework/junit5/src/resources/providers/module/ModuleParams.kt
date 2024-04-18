// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.providers.module

import com.intellij.testFramework.junit5.resources.providers.module.ModulePersistenceType.NonPersistent
import com.intellij.testFramework.junit5.resources.providers.module.ProjectSource.ProjectFromExtension
import org.jetbrains.annotations.NonNls

data class ModuleParams(val name: @NonNls ModuleName = ModuleName(),
                        val modulePersistenceType: ModulePersistenceType = NonPersistent,
                        val projectSource: ProjectSource = ProjectFromExtension,
                        val moduleTypeId: String = "")