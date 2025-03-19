// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.webSymbols.testFramework.query.doTest
import com.intellij.webSymbols.testFramework.query.printMatches
import com.intellij.webSymbols.utils.asSingleSymbol
import com.intellij.webSymbols.utils.completeMatch
import com.intellij.webSymbols.webSymbolsTestsDataPath
import com.intellij.webSymbols.webTypes.json.parseWebTypesPath

class WebSymbolsListQueryTest : WebSymbolsMockQueryExecutorTestBase() {

  override val testPath: String = "$webSymbolsTestsDataPath/query/list"

  fun testBasicElement() {
    doTest("html/elements", null, "basic")
  }

  fun testBasicAttribute() {
    doTest("html/elements/foo/attributes", null, "basic")
  }

  fun testBasicPatternAttribute() {
    doTest("html/attributes", null, true, "basic-pattern")
  }

  fun testApiStatus() {
    doTest("html/elements/", null, "api-status")
  }

  fun testVirtualAttributes() {
    doTest("html/attributes", null, true, "basic-pattern")
  }

  fun testVueDirectiveWithArguments() {
    doTest("html/elements/foo/attributes", "vue", true, "vue")
  }

  fun testVueElementWithExtends1() {
    doTest("html/elements/TransitionGroup/props", "vue", "vue")
  }

  fun testVueElementWithExtends2() {
    doTest("html/elements/TransitionGroup/attributes", "vue", "vue")
  }

  fun testVueElements() {
    doTest("html/vue-components", "vue", "vue")
  }

  fun testOldVueElement1() {
    doTest("html/elements/TransitionGroupOld/attributes", "vue", true, "vue", "vue-old")
  }

  fun testOldVueDirectiveWithArguments() {
    doTest("html/elements/foo/attributes", "vue", true, "vue", "vue-old", "events")
  }

  fun testFrameworkFiltering() {
    doTest("html/elements", null, true, "vue", "basic")
  }

  fun testCssProperties1() {
    doTest("css/properties", null, "css")
  }

  fun testCssProperties2() {
    doTest("html/elements/tag-with-css/css/properties", null, "css")
  }

  fun testCssClasses1() {
    doTest("html/elements/tag-with-css/css/classes", null, "css")
  }

  fun testNgStyleAttrs() {
    doTest("html/elements/tag-with-css/attributes", null, true, "ng-sample", "css")
  }

  fun testNgEventBinding1() {
    doTest("html/attributes", null, true, "ng-event-binding")
  }

  fun testNamingRules1() {
    doTest("html/elements/", "vue", expandPatterns = true,
           compareWithCompletionResults = false, webTypes = listOf("naming-rules"))
  }

  fun testNamingRules2() {
    doTest("html/elements/", "vue", expandPatterns = false,
           compareWithCompletionResults = false, webTypes = listOf("naming-rules"))
  }

  fun testNamingRules3() {
    doTest("js/custom-properties", "vue", expandPatterns = true,
           compareWithCompletionResults = false, webTypes = listOf("naming-rules"))
  }

  fun testNamingRules4() {
    doTest("js/custom-properties", "vue", expandPatterns = false,
           compareWithCompletionResults = false, webTypes = listOf("naming-rules"))
  }

  fun testNestedNamingRules1() {
    doTest("html/elements/", "vue", expandPatterns = true,
           compareWithCompletionResults = false, webTypes = listOf("nested-naming-rules"))
  }

  fun testLegacyVuetifyDirectives() {
    doTest("html/vue-directives", "vue", false, "vuetify-legacy", "vue")
  }

  fun testLegacyVuetifyComponents() {
    doTest("html/vue-components", "vue", false, "vuetify-legacy", "vue")
  }

  fun testLegacyVuetifyComponentProps1() {
    doTest("html/elements/VAutocomplete/props", "vue", false, "vuetify-legacy", "vue")
  }

  fun testLegacyVuetifyComponentProps2() {
    doTest("html/elements/VAutocomplete/attributes", "vue", false, "vuetify-legacy", "vue")
  }

  fun testLegacyDocVueComponent() {
    doTest("html/elements/", "vue", "vue", "doc-legacy")
  }

  fun testLegacyDocVueComponentAttribute() {
    doTest("html/elements/Foo/attributes/", "vue", true, "vue", "doc-legacy")
  }

  fun testLegacyDocVueComponentPatternSlot() {
    doTest("html/elements/Foo/slots/", "vue", "vue", "doc-legacy")
  }

  fun testLegacyDocVueDirective1() {
    doTest("html/attributes/", "vue", true, "vue", "doc-legacy")
  }

  fun testOptionalPatternNoRepeat1() {
    doTest("html/elements/", null, expandPatterns = true,
           compareWithCompletionResults = false, webTypes = listOf("optional-pattern-no-repeat"))
  }

  fun testMixedFrameworkFiles() {
    doTest("html/elements/demo-test/attributes/", "@polymer/polymer", "mixed-component", "mixed-framework")
  }

  fun testReferenceComplexNameConversion() {
    doTest("html/elements/demo-test/attributes/", null, includeVirtual = true, "mixed-component",
           "reference-with-complex-name-conversion")
  }

  fun testJsGlobalSymbols() {
    doTest("js/symbols", null, "js-globals")
  }

  fun testMultipleReferences() {
    doTest("html/attributes", null, "multiple-references")
  }

  fun testBasicCustomElementsManifest1() {
    doTest("html/elements/", customElementsManifests = listOf("basic"))
  }

  fun testBasicCustomElementsManifest2() {
    doTest("html/elements/my-EleMeNt/attributes/", customElementsManifests = listOf("basic"))
  }

  fun testBasicCustomElementsManifest4() {
    doTest("html/elements/my-EleMeNt/js/events/", customElementsManifests = listOf("basic"))
  }

  fun testBasicCustomElementsManifest5() {
    doTest("html/elements/my-EleMeNt/css/properties/", customElementsManifests = listOf("basic"))
  }

  fun testBasicCustomElementsManifest6() {
    doTest("html/elements/my-EleMeNt/css/parts/", customElementsManifests = listOf("basic"))
  }

  fun testBasicCustomElementsManifest7() {
    doTest("html/elements/my-EleMeNt/js/properties/", customElementsManifests = listOf("basic"))
  }

  fun doTest(path: String, framework: String?, vararg webTypes: String) {
    doTest(path, framework, false, *webTypes)
  }

  fun doTest(path: String, framework: String?, includeVirtual: Boolean, vararg webTypes: String) {
    doTest(path, framework, includeVirtual = includeVirtual, webTypes = webTypes.toList())
  }

  fun doTest(path: String,
             framework: String? = null,
             includeVirtual: Boolean = true,
             expandPatterns: Boolean = false,
             compareWithCompletionResults: Boolean = true,
             webTypes: List<String> = emptyList(),
             customElementsManifests: List<String> = emptyList()) {
    registerFiles(framework, webTypes, customElementsManifests)
    val parsedPath = parseWebTypesPath(path, null)
    val queryExecutor = webSymbolsQueryExecutorFactory.create(null)
    val last = parsedPath.last()

    if (compareWithCompletionResults) {
      val codeCompletionResults = queryExecutor
        .runCodeCompletionQuery(parsedPath, 0, includeVirtual)
        .filter { it.offset == 0 && !it.completeAfterInsert }
        .map { it.name }
        .distinct()
        .mapNotNull { name ->
          queryExecutor.runNameMatchQuery(parsedPath.subList(0, parsedPath.size - 1) + last.copy(name = name), includeVirtual)
            .filter { it.completeMatch }
            .asSingleSymbol()
        }
      val results = queryExecutor
        .runListSymbolsQuery(parsedPath.subList(0, parsedPath.size - 1), last.qualifiedKind,
                             true, includeVirtual, false)
        .filter { !it.extension }
      assertEquals(printMatches(codeCompletionResults), printMatches(results))
    }

    doTest(testPath) {
      queryExecutor
        .runListSymbolsQuery(parsedPath.subList(0, parsedPath.size - 1), last.qualifiedKind,
                             expandPatterns, includeVirtual, false)
        .let { printMatches(it) }
    }
  }
}