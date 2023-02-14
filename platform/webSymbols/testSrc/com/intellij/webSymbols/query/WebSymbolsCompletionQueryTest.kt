// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.webSymbols.webSymbolsTestsDataPath
import com.intellij.model.Pointer
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.StackOverflowPreventedException
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.webTypes.json.parseWebTypesPath

class WebSymbolsCompletionQueryTest : WebSymbolsMockQueryExecutorTestBase() {

  override val testPath: String = "$webSymbolsTestsDataPath/query/completion"

  fun testBasicElement() {
    doTest("html/elements/", 0, null, "basic")
    doTest("html/elements/foo", 2, null)
    doTest("html/elements", 0, null)
  }

  fun testBasicAttrs1() {
    doTest("html/elements/foo/attributes", 0, null, "basic")
    doTest("html/elements/foo/attributes/fb", 2, null)
  }

  fun testBasicAttrs2() {
    doTest("html/elements/foobar/attributes", 0, null, "basic")
  }

  fun testBasicPattern() {
    doTest("html/attributes/", 0, null, "basic-pattern")
    doTest("html/attributes/v-foo", 0, null)
    doTest("html/attributes/v-foo", 1, null)
  }

  fun testBasicPattern2() {
    doTest("html/attributes/v-", 2, null, "basic-pattern")
    doTest("html/attributes/v-foo", 2, null)
    doTest("html/attributes/v-foo", 3, null)
    doTest("html/attributes/v-foo", 4, null)
    doTest("html/attributes/v-foo", 5, null)
  }

  fun testComplexPattern1() {
    doTest("html/attributes/v-on:foo.bar", 2, "vue", "vue", "events")
  }

  fun testComplexPattern2() {
    doTest("html/attributes/v-on:click", 10, "vue", "vue", "events")
    doTest("html/attributes/v-on:click.stop", 10, "vue")
  }

  fun testComplexPattern3() {
    doTest("html/attributes/v-on:click.stop", 11, "vue", "vue", "events")
    doTest("html/attributes/v-on:click.stop", 12, "vue")
  }

  fun testComplexPattern4() {
    doTest("html/attributes/v-o", 3, "vue", "vue-on", "events")
  }

  fun testComplexPattern5() {
    doTest("html/attributes/v-on", 4, "vue", "vue-on", "events")
  }

  fun testComplexPattern6() {
    doTest("html/attributes/v-on:", 5, "vue", "vue-on", "events")
  }

  fun testComplexPattern7() {
    doTest("html/attributes/v-on:c", 6, "vue", "vue-on", "events")
  }

  fun testComplexPattern8() {
    doTest("html/elements/Transition/attributes/v-bind:appear", 9, "vue", "vue")
  }

  fun testComplexPatternRepeat1() {
    doTest("html/attributes/v-on:click.stop.prevent", 23, "vue", "vue-on", "events")
    doTest("html/attributes/v-on:click.stop.prevent.foo", 23, "vue")
    doTest("html/attributes/v-on:click.stop.prevent.foo", 24, "vue")
    doTest("html/attributes/v-on:click.stop.prevent.capture", 23, "vue")
    doTest("html/attributes/v-on:click.stop.prevent.capture", 24, "vue")
  }

  fun testComplexPatternRepeat2() {
    doTest("html/attributes/v-on:click.stop.prevenn", 23, "vue", "vue-on", "events")
  }

  fun testComplexPatternRepeat3() {
    doTest("html/attributes/v-on:click.stop.prevenn.prevent", 23, "vue", "vue-on", "events")
  }

  fun testStickyExpansion1() {
    doTest("html/attributes/@", 1, "vue", "vue-on", "events")
    doTest("html/attributes/@cli", 1, "vue")
    doTest("html/attributes/@click", 1, "vue")
  }

  fun testStickyExpansion2() {
    doTest("html/attributes/@cli", 2, "vue", "vue-on", "events")
    doTest("html/attributes/@click", 2, "vue")
  }

  fun testVueElementProps() {
    doTest("html/elements/TransitionGroup/attributes/", 0, "vue", "vue")
  }

  fun testVueElementEvents() {
    doTest("html/elements/TransitionGroup/attributes/v-on:appear.stop", 5, "vue", "vue", "events")
  }

  fun testVueEventBind() {
    doTest("html/attributes/v", 1, "vue", "vue", "events")
  }

  fun testComplexCssClasses() {
    doTest("html/elements/tag-with-css/attributes/cls-foo", 4, null, "css")
  }

  fun testNgStyleAttrs1() {
    doTest("html/elements/tag-with-css/attributes/", 0, null, "ng-sample", "css")
  }

  fun testNgStyleAttrs2() {
    doTest("html/elements/tag-with-css/attributes/[class.foo]", 0, null, "ng-sample", "css")
    doTest("html/elements/tag-with-css/attributes/[class.foo]", 1, null)
    doTest("html/elements/tag-with-css/attributes/[class.foo]", 2, null)
    doTest("html/elements/tag-with-css/attributes/[class.foo]", 5, null)
    doTest("html/elements/tag-with-css/attributes/[class.foo]", 6, null)
  }

  fun testNgStyleAttrs3() {
    doTest("html/elements/tag-with-css/attributes/[class.foo]", 7, null, "ng-sample", "css")
  }

  fun testNgStyleAttrs4() {
    doTest("html/elements/tag-with-css/attributes/[class.foo]", 8, null, "ng-sample", "css")
    doTest("html/elements/tag-with-css/attributes/[class.foo]", 9, null)
    doTest("html/elements/tag-with-css/attributes/[class.foo]", 10, null)
  }

  fun testNgStyleAttrs5() {
    doTest("html/elements/tag-with-css/attributes/data-[class.foo]", 5, null, "ng-sample", "css")
  }

  fun testNgStyleAttrs6() {
    doTest("html/elements/tag-with-css/attributes/data-", 5, null, "ng-sample", "css")
  }

  fun testNgStyleAttrs7() {
    doTest("html/elements/tag-with-css/attributes/[class.", 7, null, "ng-sample", "css")
  }

  fun testEventPriorityPropagation() {
    doTest("html/attributes", 0, null, "ng-event-binding")
  }

  fun testEventNamedItems() {
    doTest("html/attributes/a", 1, null, "ng-event-binding")
  }

  fun testNestedEventPattern1() {
    doTest("html/attributes/(ke", 3, null, "ng-event-binding")
  }

  fun testNestedEventPattern2() {
    doTest("html/attributes/(keyup", 6, null, "ng-event-binding")
  }

  fun testNestedEventPattern3() {
    doTest("html/attributes/(keyup.", 7, null, "ng-event-binding")
  }

  fun testNestedEventPattern4() {
    doTest("html/attributes/(keyup.al", 9, null, "ng-event-binding")
  }

  fun testNestedEventPattern5() {
    doTest("html/attributes/(keyup.alt.", 11, null, "ng-event-binding")
  }

  fun testNestedEventInnerPattern1() {
    doTest("js/ng-custom-events/ke", 2, null, "ng-event-binding")
  }

  fun testNestedEventInnerPattern2() {
    doTest("js/ng-custom-events/keyup", 5, null, "ng-event-binding")
  }

  fun testNestedEventInnerPattern3() {
    doTest("js/ng-custom-events/keyup.", 6, null, "ng-event-binding")
  }

  fun testNestedEventInnerPattern4() {
    doTest("js/ng-custom-events/keyup.al", 8, null, "ng-event-binding")
  }

  fun testNestedEventInnerPattern5() {
    doTest("js/ng-custom-events/keyup.alt.", 10, null, "ng-event-binding")
  }

  fun testNamingRules1() {
    doTest("html/elements/hello-11Wo-rld", 2, "vue", "naming-rules")
  }

  fun testNamingRules2() {
    doTest("js/properties", 0, "vue", "naming-rules")
  }

  fun testNestedNamingRules1() {
    doTest("html/elements/hello-11Wo-rld", 2, "vue", "nested-naming-rules")
  }

  fun testNestedNamingRules2() {
    doTest("html/elements/hello-", 2, "vue", "nested-naming-rules")
  }

  fun testFilter1() {
    doTest("html/attributes", 0, null, "filter")
  }

  fun testNestedPattern1() {
    webSymbolsQueryExecutorFactory.addScope(
      object : WebSymbolsScope {
        override fun createPointer(): Pointer<out WebSymbolsScope> = Pointer.hardPointer(this)

        override fun getCodeCompletions(namespace: SymbolNamespace,
                                        kind: SymbolKind,
                                        name: String?,
                                        params: WebSymbolsCodeCompletionQueryParams,
                                        scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> {
          return if (kind == WebSymbol.KIND_HTML_ATTRIBUTES) {
            listOf(WebSymbolCodeCompletionItem.create("bar"))
          }
          else emptyList()
        }

        override fun getModificationCount(): Long = 0
      }, null, testRootDisposable)
    doTest("html/elements/", 0, "vue", "nested-pattern")
  }

  fun testLegacyVuetifyDirectives() {
    doTest("html/vue-directives", 0, "vue", "vuetify-legacy", "vue")
  }

  fun testLegacyVuetifyComponents() {
    doTest("html/vue-components", 0, "vue", "vuetify-legacy", "vue")
  }

  fun testLegacyVuetifyComponentProps1() {
    doTest("html/elements/VAlert/props", 0, "vue", "vuetify-legacy", "vue")
  }

  fun testLegacyVuetifyComponentProps2() {
    doTest("html/elements/VAlert/attributes", 0, "vue", "vuetify-legacy", "vue")
  }

  fun testLegacyVuetifyComponentProps3() {
    doTest("html/elements/VAlert/attributes/v-touch:foo", 9, "vue", "vuetify-legacy", "vue")
  }

  fun testOptionalPatternNoRepeat1() {
    doTest("html/elements/icon-vuetify", 0, null,"optional-pattern-no-repeat")
  }

  fun testOptionalPatternNoRepeat2() {
    doTest("html/elements/icon-vuetify", 5, null,"optional-pattern-no-repeat")
  }

  fun testOptionalPatternNoRepeat3() {
    doTest("html/elements/icon-vue", 8, null,"optional-pattern-no-repeat")
  }

  fun testOptionalPatternNoRepeat4() {
    doTest("html/elements/icon-vue-", 9, null,"optional-pattern-no-repeat")
  }

  fun testRecursiveQuery() {
    RecursionManager.assertOnRecursionPrevention(testRootDisposable)
    assertThrows(StackOverflowPreventedException::class.java) {
      doTest("html/elements/i/attributes", 0, null, "recursive")
    }
  }

  fun testMixedFrameworkFiles() {
    doTest("html/elements/demo-test/attributes/", 0, "@polymer/polymer", "mixed-component", "mixed-framework")
  }

  fun testReferenceNameConversion() {
    doTest("html/elements/demo-test/attributes/", 0, "@polymer/polymer", "mixed-component", "reference-with-name-conversion")
  }

  fun testReferenceComplexNameConversion() {
    doTest("html/elements/demo-test/attributes/", 0, null, "mixed-component", "reference-with-complex-name-conversion")
  }

  private fun doTest(path: String, position: Int, framework: String?, vararg webTypes: String) {
    doTest(testPath) {
      registerFiles(framework, *webTypes)
      val matches = webSymbolsQueryExecutorFactory.create(null)
        .runCodeCompletionQuery(parseWebTypesPath(path, null), position)
      printCodeCompletionItems(matches)
    }
  }

}