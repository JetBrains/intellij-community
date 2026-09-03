// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text.simpleGlob

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test

class SimpleGlobTest {
  @Test
  fun starStaysInOneSegment() {
    assertGlob("*", "^[^/]*$", listOf("lodash.js"), listOf("a/lodash.js"))
    assertGlob("*.js", "^[^/]*\\.js$", listOf("x.js"), listOf("x.json", "a/x.js"))
  }

  @Test
  fun questionMarkMatchesOneCharacter() {
    assertGlob("a?b", "^a[^/]b$", listOf("axb"), listOf("ab", "axxb", "a/b"))
    assertGlob("a?", "^a[^/]$", listOf("ax"), listOf("a", "axx"))
  }

  @Test
  fun globstarCrossesSlash() {
    assertGlob("**", "^(.+/)?", listOf("x.js", "a/b/x.js"))
    assertGlob("**/*.js", "^(.+/)?[^/]*\\.js$", listOf("x.js", "a/x.js", "a/b/x.js"), listOf("a/x.ts"))
    assertGlob("**/x.js", "^(.+/)?x\\.js$", listOf("x.js", "a/b/x.js"), listOf("a/yx.js"))
    assertGlob("a/**/b", "^a\\/.*/b$", listOf("a/x/b", "a/x/y/b"), listOf("a/b"))
  }

  @Test
  fun globstarWorksOnlyAsWholeSegment() {
    assertGlob("a**b", "^a[^/]*[^/]*b$", listOf("ab", "aXb"), listOf("aX/Yb"))
  }

  @Test
  fun trailingGlobstarMatchesAPrefix() {
    assertGlob("a/**", "^a\\/", listOf("a/", "a/b", "a/b/c"), listOf("a", "b/a/c"))
    assertGlob("vm:**", "^vm:.*", listOf("vm:module", "vm:a/b/c"), listOf("Xvm:module"))
  }

  @Test
  fun patternWithoutAWildcardMatchesTheWholeUrl() {
    assertGlob("foo.js", "^foo\\.js$", listOf("foo.js"), listOf("a/foo.js", "fooXjs"))
  }

  @Test
  fun regexMetaCharactersAreEscaped() {
    assertGlob("a+b(c)", "^a\\+b\\(c\\)$", listOf("a+b(c)"), listOf("aab"))
  }

  @Test
  fun negationFoldsIntoEveryPrecedingPattern() {
    val res = simpleGlobsToRe(listOf("**/node_modules/**", "!**/node_modules/mylib/**"))

    assertThat(res).containsExactly("^(?!(.+/)?node_modules\\/mylib\\/)(.+/)?node_modules\\/")

    val compiled = Regex(res.single())
    SoftAssertions.assertSoftly { softly ->
      softly.assertThat(compiled.containsMatchIn("/h/node_modules/other/i.js")).isTrue()
      softly.assertThat(compiled.containsMatchIn("node_modules/x.js")).isTrue()
      softly.assertThat(compiled.containsMatchIn("/h/node_modules/mylib/i.js")).isFalse()
      softly.assertThat(compiled.containsMatchIn("node_modules/mylib/i.js")).isFalse()
    }
  }

  @Test
  fun negationDoesNotAffectAFollowingPattern() {
    val res = simpleGlobsToRe(listOf("!a/**", "a/**"))

    assertThat(res).containsExactly("^a\\/")
  }

  @Test
  fun aListOfOnlyNegationsProducesNoPattern() {
    assertThat(simpleGlobsToRe(listOf("!a/**", "!b/**"))).isEmpty()
  }

  @Test
  fun theDebuggerDefaultPatternsMatchRealScriptUrls() {
    assertGlob("vm:**", "^vm:.*", listOf("vm:module"))
    assertGlob("node:**", "^node:.*", listOf("node:fs", "node:internal/modules/cjs/loader"), listOf("nodeXfs"))
    assertGlob("internal/**",
               "^internal\\/",
               listOf("internal/fs.js", "internal/modules/cjs/loader.js"),
               listOf("node:internal/fs.js"))
    assertGlob("**/node_modules/**",
               "^(.+/)?node_modules\\/",
               listOf("node_modules/l.js",
                                "/h/u/node_modules/l.js",
                                "file:///h/node_modules/a/b/l.js",
                                "http://h/node_modules/l.js",
                                "webpack:///./node_modules/l.js"),
               listOf("node_modulesX/l.js"))
    assertGlob("*://*/webpack/**",
               "^[^/]*:\\/\\/[^/]*\\/webpack\\/",
               listOf("http://h/webpack/b.js",
                      "http://h/webpack/a/b.js",
                      "https://h/webpack/b.js",
                      "webpack:///webpack/a/b.js",
                      "file:///webpack/x.js",
                      "webpack://h/webpack/x.js"),
               listOf("http://h/webpackX/b.js", "http://h/a/webpack/b.js"))
  }

  private fun assertGlob(glob: String, regex: String, matches: List<String> = emptyList(), rejects: List<String> = emptyList()) {
    val res = simpleGlobsToRe(listOf(glob))

    assertThat(res).describedAs("the regex list of '%s'", glob).hasSize(1)
    assertThat(res.single()).describedAs("the regex of '%s'", glob).isEqualTo(regex)

    val compiled = Regex(res.single())
    SoftAssertions.assertSoftly { softly ->
      for (url in matches) {
        softly.assertThat(compiled.containsMatchIn(url)).describedAs("'%s' must match '%s'", glob, url).isTrue()
      }
      for (url in rejects) {
        softly.assertThat(compiled.containsMatchIn(url)).describedAs("'%s' must not match '%s'", glob, url).isFalse()
      }
    }
  }
}
