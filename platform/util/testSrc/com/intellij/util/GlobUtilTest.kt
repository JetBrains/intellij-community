// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.util.getPathMatcher
import junit.framework.TestCase
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.regex.PatternSyntaxException

class GlobUtilTest : TestCase() {
  private fun PathMatcher.checkMatches(path: String) = assertTrue("path doesn't match: $path", matches(Path.of(path)))
  private fun PathMatcher.checkDoesntMatch(path: String) = assertFalse("path matches but must not: $path", matches(Path.of(path)))

  fun testPathMatcher() {
    val m1 = getPathMatcher("**/foo.txt")
    m1.checkMatches("foo.txt")
    m1.checkMatches("/foo.txt")
    m1.checkMatches("./foo.txt")
    m1.checkMatches("q/d/v/s/foo.txt/foo.txt")
    m1.checkDoesntMatch("q/d/v/s/foo.txt/foo.tx")
    m1.checkDoesntMatch("q/d/v/s/ffoo.txt")
    m1.checkDoesntMatch("fo.txt")

    val m2 = getPathMatcher("src/**/{a,b}.{1,2}")
    m2.checkMatches("src/a.1")
    m2.checkMatches("src/foo/a.2")
    m2.checkMatches("src/foo/bar/baz/b.2")
    m2.checkDoesntMatch("/src/a.1")
    m2.checkDoesntMatch("src/foo/ab.1")
    m2.checkDoesntMatch("src/foo/bar/baz/b.3")

    val m3 = getPathMatcher("**/a/**/b/**/c/*")
    m3.checkMatches("a/b/c/d")
    m3.checkMatches("a/b/c/d.txt")
    m3.checkMatches("1/2/3/a/b/c/d.txt")
    m3.checkMatches("a/4/5/b/c/d.txt")
    m3.checkMatches("a/b/6/c/d.txt")
    m3.checkMatches("1/a/b/6/7/c/d.txt")
    m3.checkMatches("a/2/b/6/7/c/d.txt")
    m3.checkMatches("1/a/2/3/b/c/d.txt")
    m3.checkMatches("1/2/a/3/4/b/5/6/c/d.txt")
    m3.checkDoesntMatch("a/b/c")
    m3.checkDoesntMatch("a/b/c/d/e.txt")
    m3.checkDoesntMatch("1a/b/c/d.txt")
    m3.checkDoesntMatch("a/2b/c/d.txt")
    m3.checkDoesntMatch("a/b/3c/d.txt")

    val m4 = getPathMatcher("glob:**/a/**/b/{**/c/**/d/.*,e.*}")
    m4.checkMatches("a/b/c/d/.txt")
    m4.checkMatches("a/b/e.txt")
    m4.checkMatches("1/1/a/b/c/d/.txt")
    m4.checkMatches("a/2/2/b/c/d/.txt")
    m4.checkMatches("a/b/3/4/c/d/.txt")
    m4.checkMatches("a/b/c/4/4/d/.txt")
    m4.checkMatches("1/a/2/b/c/d/.txt")
    m4.checkMatches("1/a/b/3/c/d/.txt")
    m4.checkMatches("1/a/b/c/4/d/.txt")
    m4.checkMatches("a/2/b/3/c/d/.txt")
    m4.checkMatches("a/2/b/c/4/d/.txt")
    m4.checkMatches("a/b/3/c/4/d/.txt")
    m4.checkMatches("1/a/2/2/b/3/3/c/d/.txt")
    m4.checkMatches("a/2/b/3/c/4/d/.txt")
    m4.checkMatches("1/a/b/3/c/4/d/.txt")
    m4.checkMatches("1/a/2/b/c/4/d/.txt")
    m4.checkMatches("1/a/2/b/3/c/4/d/.txt")
    m4.checkDoesntMatch("1/a/2/b/3/c/4/d/x.txt")
    m4.checkDoesntMatch("a/b/c")
    m4.checkDoesntMatch("a/b/c/d/e.txt")
  }

  fun testPatternSyntaxException() {
    getPathMatcher("exception ignored \\")
    getPathMatcher("exception ignored \\", true)
    try {
      getPathMatcher("exception thrown \\", false)
      fail("Expected PatternSyntaxException not thrown")
    }
    catch (e: PatternSyntaxException) {
      // ok
    }
  }
}