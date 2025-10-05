// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common.mock

/**
 * When mocking an object, throw this for a stubbed member to indicate that its usage by the production code is not expected in the test.
 */
class NotMockedMemberError : IllegalStateException("The accessed member is not mocked. Please mock it in the test.")