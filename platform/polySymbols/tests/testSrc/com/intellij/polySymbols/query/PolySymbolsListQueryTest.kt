// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolsTestsDebugOutputPrinter
import com.intellij.polySymbols.polySymbolsTestsDataPath
import com.intellij.polySymbols.testFramework.query.printMatches
import com.intellij.polySymbols.utils.asSingleSymbol
import com.intellij.polySymbols.utils.completeMatch
import com.intellij.polySymbols.webTypes.json.parseWebTypesPath
import com.intellij.testFramework.junit5.fixture.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestFixtures
class PolySymbolsListQueryTest {

  private val mock by polySymbolsMockQueryExecutorFixture("$polySymbolsTestsDataPath/query/list")

  @Test
  fun testBasicElement() {
    doTest("html/elements", null, "basic")
  }

  @Test
  fun testBasicAttribute() {
    doTest("html/elements/foo/attributes", null, "basic")
  }

  @Test
  fun testBasicPatternAttribute() {
    doTest("html/attributes", null, true, "basic-pattern")
  }

  @Test
  fun testApiStatus() {
    doTest("html/elements/", null, "api-status")
  }

  @Test
  fun testVirtualAttributes() {
    doTest("html/attributes", null, true, "basic-pattern")
  }

  @Test
  fun testVueDirectiveWithArguments() {
    doTest("html/elements/foo/attributes", "vue", true, "vue")
  }

  @Test
  fun testVueElementWithExtends1() {
    doTest("html/elements/TransitionGroup/props", "vue", "vue")
  }

  @Test
  fun testVueElementWithExtends2() {
    doTest("html/elements/TransitionGroup/attributes", "vue", "vue")
  }

  @Test
  fun testVueElements() {
    doTest("html/vue-components", "vue", "vue")
  }

  @Test
  fun testOldVueElement1() {
    doTest("html/elements/TransitionGroupOld/attributes", "vue", true, "vue", "vue-old")
  }

  @Test
  fun testOldVueDirectiveWithArguments() {
    doTest("html/elements/foo/attributes", "vue", true, "vue", "vue-old", "events")
  }

  @Test
  fun testFrameworkFiltering() {
    doTest("html/elements", null, true, "vue", "basic")
  }

  @Test
  fun testCssProperties1() {
    doTest("css/properties", null, "css")
  }

  @Test
  fun testCssProperties2() {
    doTest("html/elements/tag-with-css/css/properties", null, "css")
  }

  @Test
  fun testCssClasses1() {
    doTest("html/elements/tag-with-css/css/classes", null, "css")
  }

  @Test
  fun testNgStyleAttrs() {
    doTest("html/elements/tag-with-css/attributes", null, true, "ng-sample", "css")
  }

  @Test
  fun testNgEventBinding1() {
    doTest("html/attributes", null, true, "ng-event-binding")
  }

  @Test
  fun testNamingRules1() {
    doTest("html/elements/", "vue", expandPatterns = true,
           compareWithCompletionResults = false, webTypes = listOf("naming-rules"))
  }

  @Test
  fun testNamingRules2() {
    doTest("html/elements/", "vue", expandPatterns = false,
           compareWithCompletionResults = false, webTypes = listOf("naming-rules"))
  }

  @Test
  fun testNamingRules3() {
    doTest("js/custom-properties", "vue", expandPatterns = true,
           compareWithCompletionResults = false, webTypes = listOf("naming-rules"))
  }

  @Test
  fun testNamingRules4() {
    doTest("js/custom-properties", "vue", expandPatterns = false,
           compareWithCompletionResults = false, webTypes = listOf("naming-rules"))
  }

  @Test
  fun testNestedNamingRules1() {
    doTest("html/elements/", "vue", expandPatterns = true,
           compareWithCompletionResults = false, webTypes = listOf("nested-naming-rules"))
  }

  @Test
  fun testLegacyVuetifyDirectives() {
    doTest("html/vue-directives", "vue", false, "vuetify-legacy", "vue")
  }

  @Test
  fun testLegacyVuetifyComponents() {
    doTest("html/vue-components", "vue", false, "vuetify-legacy", "vue")
  }

  @Test
  fun testLegacyVuetifyComponentProps1() {
    doTest("html/elements/VAutocomplete/props", "vue", false, "vuetify-legacy", "vue")
  }

  @Test
  fun testLegacyVuetifyComponentProps2() {
    doTest("html/elements/VAutocomplete/attributes", "vue", false, "vuetify-legacy", "vue")
  }

  @Test
  fun testLegacyDocVueComponent() {
    doTest("html/elements/", "vue", "vue", "doc-legacy")
  }

  @Test
  fun testLegacyDocVueComponentAttribute() {
    doTest("html/elements/Foo/attributes/", "vue", true, "vue", "doc-legacy")
  }

  @Test
  fun testLegacyDocVueComponentPatternSlot() {
    doTest("html/elements/Foo/slots/", "vue", "vue", "doc-legacy")
  }

  @Test
  fun testLegacyDocVueDirective1() {
    doTest("html/attributes/", "vue", true, "vue", "doc-legacy")
  }

  @Test
  fun testOptionalPatternNoRepeat1() {
    doTest("html/elements/", null, expandPatterns = true,
           compareWithCompletionResults = false, webTypes = listOf("optional-pattern-no-repeat"))
  }

  @Test
  fun testMixedFrameworkFiles() {
    doTest("html/elements/demo-test/attributes/", "@polymer/polymer", "mixed-component", "mixed-framework")
  }

  @Test
  fun testReferenceComplexNameConversion() {
    doTest("html/elements/demo-test/attributes/", null, includeVirtual = true, "mixed-component",
           "reference-with-complex-name-conversion")
  }

  @Test
  fun testJsGlobalSymbols() {
    doTest("js/symbols", null, "js-globals")
  }

  @Test
  fun testMultipleReferences() {
    doTest("html/attributes", null, "multiple-references")
  }

  @Test
  fun testBasicCustomElementsManifest1() {
    doTest("html/elements/", customElementsManifests = listOf("basic"))
  }

  @Test
  fun testBasicCustomElementsManifest2() {
    doTest("html/elements/my-EleMeNt/attributes/", customElementsManifests = listOf("basic"))
  }

  @Test
  fun testBasicCustomElementsManifest4() {
    doTest("html/elements/my-EleMeNt/js/events/", customElementsManifests = listOf("basic"))
  }

  @Test
  fun testBasicCustomElementsManifest5() {
    doTest("html/elements/my-EleMeNt/css/properties/", customElementsManifests = listOf("basic"))
  }

  @Test
  fun testBasicCustomElementsManifest6() {
    doTest("html/elements/my-EleMeNt/css/parts/", customElementsManifests = listOf("basic"))
  }

  @Test
  fun testBasicCustomElementsManifest7() {
    doTest("html/elements/my-EleMeNt/js/properties/", customElementsManifests = listOf("basic"))
  }

  fun doTest(path: String, framework: String?, vararg webTypes: String) {
    doTest(path, framework, false, *webTypes)
  }

  fun doTest(path: String, framework: String?, includeVirtual: Boolean, vararg webTypes: String) {
    doTest(path, framework, includeVirtual = includeVirtual, webTypes = webTypes.toList())
  }

  fun doTest(
    path: String,
    framework: String? = null,
    includeVirtual: Boolean = true,
    expandPatterns: Boolean = false,
    compareWithCompletionResults: Boolean = true,
    webTypes: List<String> = emptyList(),
    customElementsManifests: List<String> = emptyList(),
  ) {
    mock.registerFiles(framework, webTypes, customElementsManifests)
    val parsedPath = parseWebTypesPath(path, null)
    val queryExecutor = mock.polySymbolQueryExecutorFactory.create(null)
    val last = parsedPath.last()

    if (compareWithCompletionResults) {
      val codeCompletionResults = queryExecutor
        .codeCompletionQuery(parsedPath, 0) {
          if (!includeVirtual) exclude(PolySymbolModifier.VIRTUAL)
          exclude(PolySymbolModifier.ABSTRACT)
        }
        .filter { it.offset == 0 && !it.completeAfterInsert }
        .map { it.name }
        .distinct()
        .mapNotNull { name ->
          queryExecutor.nameMatchQuery(parsedPath.subList(0, parsedPath.size - 1) + last.withName(name)) {
            if (!includeVirtual) exclude(PolySymbolModifier.VIRTUAL)
            exclude(PolySymbolModifier.ABSTRACT)
          }
            .filter { it.completeMatch }
            .asSingleSymbol()
        }
      val results = queryExecutor
        .listSymbolsQuery(parsedPath.subList(0, parsedPath.size - 1), last.kind, true) {
          if (!includeVirtual) exclude(PolySymbolModifier.VIRTUAL)
          exclude(PolySymbolModifier.ABSTRACT)
        }
        .filter { !it.extension }
      assertEquals(printMatches(codeCompletionResults, PolySymbolsTestsDebugOutputPrinter),
                   printMatches(results, PolySymbolsTestsDebugOutputPrinter))
    }

    mock.doTest {
      queryExecutor
        .listSymbolsQuery(parsedPath.subList(0, parsedPath.size - 1), last.kind, expandPatterns) {
          if (!includeVirtual) exclude(PolySymbolModifier.VIRTUAL)
          exclude(PolySymbolModifier.ABSTRACT)
        }
        .let { printMatches(it, PolySymbolsTestsDebugOutputPrinter) }
    }
  }
}