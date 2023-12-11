// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.codeInspection.ex.InspectionElementsMerger

class JUnitMalformedDeclarationInspectionMerger : InspectionElementsMerger() {
  override fun getMergedToolName(): String = "JUnitMalformedDeclaration"

  override fun getSourceToolNames(): Array<String> = arrayOf(
    "JUnitTestCaseWithNonTrivialConstructors",
    "JUnitRule",
    "JUnit5MalformedNestedClass",
    "JUnit5MalformedExtensions",
    "Junit5MalformedParameterized",
    "BeforeOrAfterIsPublicVoidNoArg",
    "BeforeClassOrAfterClassIsPublicStaticVoidNoArg",
    "JUnit5MalformedRepeated",
    "JUnitDatapoint",
    "TestMethodIsPublicVoidNoArg",
    "MalformedSetUpTearDown",
    "TeardownIsPublicVoidNoArg",
    "SetupIsPublicVoidNoArg"
  )

  override fun getSuppressIds(): Array<String> = arrayOf(
    "JUnitTestCaseWithNonTrivialConstructors",
    "JUnitRule",
    "JUnit5MalformedNestedClass",
    "JUnit5MalformedExtensions",
    "Junit5MalformedParameterized",
    "BeforeOrAfterWithIncorrectSignature",
    "BeforeOrAfterWithIncorrectSignature",
    "JUnit5MalformedRepeated",
    "JUnitDatapoint",
    "TestMethodWithIncorrectSignature",
    "MalformedSetUpTearDown",
    "TearDownWithIncorrectSignature",
    "SetUpWithIncorrectSignature"
  )
}