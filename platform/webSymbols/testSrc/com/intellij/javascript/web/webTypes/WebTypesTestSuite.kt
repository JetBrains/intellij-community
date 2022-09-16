package com.intellij.javascript.web.webTypes

import com.intellij.javascript.web.webTypes.registry.WebTypesRegistryCompletionQueryTest
import com.intellij.javascript.web.webTypes.registry.WebTypesRegistryNameQueryTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
  WebTypesRegistryCompletionQueryTest::class,
  WebTypesRegistryNameQueryTest::class,
)
class WebTypesTestSuite {
}