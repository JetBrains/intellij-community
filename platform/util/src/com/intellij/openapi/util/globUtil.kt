// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.regex.PatternSyntaxException

/**
 * This function is similar to [FileSystems.getDefault().getPathMatcher("glob: ...")][FileSystem.getPathMatcher]
 * (and uses that one internally),
 * but this function handles [globstars](https://code.visualstudio.com/docs/editor/glob-patterns) (`**`)
 * better than the JDK's native glob path matcher implementation.
 *
 * The JDK's implementation of `FileSystem.getPathMatcher()` says that
 * the path `foo.txt` doesn't match the glob pattern &#42;&#42;`/foo.txt`,
 * or that the path `src/foo.txt` doesn't match the glob pattern `src/`&#42;&#42;`/foo.txt`.
 * The reason is that the JDK's implementation adds mandatory slash to the regexp, which it uses under the hood.
 * For example, `FileSystem.getPathMatcher()` translates the glob pattern &#42;&#42;`/foo.txt` into the following regexp:
 * `^.`&#42;`/[^/]`&#42;`\.txt$`.
 * So, `foo.txt` doesn't match because such regexp expects a slash before `foo.txt`.
 *
 * The `PathMatcher` returned by this function works with globstars as expected.
 * For example, the path `foo.txt` does match the pattern &#42;&#42;`/foo.txt`,
 * and the path `src/foo.txt` does match the pattern `src/`&#42;&#42;`/foo.txt`.
 *
 * If the [globPattern] is invalid and [ignorePatternSyntaxException] is `true` (and by default it is `true`) then the returned
 * `PathMatcher` always returns `false` from its `matches(...)` method.
 *
 * @param globPattern the passed argument doesn't need to have `glob:` prefix (though no difference if it does have this prefix)
 * @throws PatternSyntaxException if [ignorePatternSyntaxException] is `false` and the [globPattern] is invalid
 */
@ApiStatus.Internal
fun getPathMatcher(globPattern: String, ignorePatternSyntaxException: Boolean = true): PathMatcher {
  val patterns: List<String> = transformIntoJdkFriendlyGlobs(globPattern.removePrefix("glob:"))

  val matchers: List<PathMatcher> = patterns.mapNotNull {
    try {
      FileSystems.getDefault().getPathMatcher("glob:$it")
    }
    catch (e: PatternSyntaxException) {
      if (ignorePatternSyntaxException) null else throw e
    }
  }

  return if (matchers.size == 1) matchers[0] else PathMatcher { path: Path -> matchers.any { it.matches(path) } }
}

/**
 * Transforms the provided [globPattern] and/or splits it into several glob patterns as needed for further usage as an argument of the
 * [FileSystem.getPathMatcher] function. The transformation is needed to work around the JDK limitations, as described in [getPathMatcher].
 */
private fun transformIntoJdkFriendlyGlobs(globPattern: String): List<String> {
  // Example: glob pattern `**/{foo/**/bar/**/baz,123}` splits into 4 JDK-friendly globs, which together cover all cases:
  // `{**/,}{foo/**/bar/**/baz,123}`
  // `{**/,}{foo/bar/**/baz,123}`
  // `{**/,}{foo/**/bar/baz,123}`
  // `{**/,}{foo/bar/baz,123}`

  // `FileSystem.getPathMatcher()` doesn't support nested groups. Need to make sure we don't introduce such.
  val braceIndex = globPattern.indexOf('{')
  val globBeforeBrace = if (braceIndex == -1) globPattern else globPattern.substring(0, braceIndex)
  val globTail = globPattern.substring(globBeforeBrace.length)
  // We can safely replace `**/` with `{**/,}` only while we are sure we don't introduce nested groups.
  val fixedGlobBeforeBrace = globBeforeBrace.replace("**/", "{**/,}")

  val result = mutableListOf(fixedGlobBeforeBrace + globTail)

  var globstarIndex = globTail.indexOf("**/")
  var globstarsInTail = 0
  while (globstarIndex >= 0) {
    globstarsInTail++
    // Replace "**/" with "" to handle the case when `**` matches zero path segments.
    result.add(fixedGlobBeforeBrace + globTail.replaceRange(globstarIndex, globstarIndex + "**/".length, ""))
    globstarIndex = globTail.indexOf("**/", globstarIndex + 1)
  }

  if (globstarsInTail > 1) {
    result.add(fixedGlobBeforeBrace + globTail.replace("**/", ""))
    // All cases when globstarsInTail <= 2 are covered, but some exotic cases when globstarsInTail > 2 won't work (too many combinations).
  }

  return result
}