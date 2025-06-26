// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model

class NotMockedMemberError : IllegalStateException("The accessed member is not mocked. Please mock it in the test.")