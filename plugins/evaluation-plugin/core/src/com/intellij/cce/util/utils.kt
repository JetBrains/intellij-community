// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.util

val isUnderTeamCity: Boolean = System.getenv("TEAMCITY_VERSION") != null

val isUnderZenML: Boolean = System.getenv("ZENML_VERSION") != null