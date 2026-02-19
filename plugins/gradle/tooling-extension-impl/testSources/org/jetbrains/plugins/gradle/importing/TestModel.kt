// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import java.io.Serializable

interface TestModel : Serializable {

  class Model1 : TestModel

  class Model2 : TestModel
}