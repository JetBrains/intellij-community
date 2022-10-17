// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus.model

data class SortRequestEntity(
  val changes: List<ChangeEntity>,
  val tests: List<TestCaseEntity>
)