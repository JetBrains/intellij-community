// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang

import com.intellij.util.currentJavaVersionPlatformSpecific
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A class representing a version of some Java platform - e.g. the runtime the class is loaded into, or some installed JRE.
 *
 * Based on [JEP 322 "Time-Based Release Versioning"](http://openjdk.org/jeps/322) (Java 10+), but also supports JEP 223
 * "New Version-String Scheme" (Java 9), as well as earlier version's formats.
 *
 * See [.parse] for examples of supported version strings.
 *
 * @implNote the class is used in bootstrap - please only use runtime API
 */
class JavaVersion private constructor(
  /**
   * The major version.
   * Corresponds to the first number of the 9+ format (**9**.0.1) / the second number of the 1.x format (1.**8**.0_60).
   */
  @JvmField
  val feature: Int,

  /**
   * The minor version.
   * Corresponds to the second number of the 9+ format (9.**0**.1) / the third number of 1.x the format (1.8.**0**_60).
   * Was used in version strings prior to 1.5, in newer strings is always `0`.
   */
  @JvmField
  val minor: Int,

  /**
   * The patch version.
   * Corresponds to the third number of the 9+ format (9.0.**1**) / the number after an underscore of the 1.x format (1.8.0_**60**).
   */
  @JvmField
  val update: Int,

  /**
   * The build number.
   * Corresponds to a number prefixed by the "plus" sign in the 9+ format (9.0.1+**7**) /
   * by "-b" string in the 1.x format (1.8.0_60-b**12**).
   */
  @JvmField
  val build: Int,

  /**
   * `true` if the platform is an early access release, `false` otherwise (or when not known).
   */
  @JvmField
  val ea: Boolean,
) : Comparable<JavaVersion> {
  init {
    require(feature >= 0)
    require(minor >= 0)
    require(update >= 0)
    require(build >= 0)
  }

  override fun compareTo(other: JavaVersion): Int {
    var diff = feature - other.feature
    if (diff != 0) return diff
    diff = minor - other.minor
    if (diff != 0) return diff
    diff = update - other.update
    if (diff != 0) return diff
    diff = build - other.build
    if (diff != 0) return diff
    return (if (ea) 0 else 1) - (if (other.ea) 0 else 1)
  }

  fun isAtLeast(feature: Int): Boolean = this.feature >= feature

  override fun equals(other: Any?): Boolean =
    this === other ||
    other is JavaVersion && feature == other.feature && minor == other.minor && update == other.update && build == other.build && ea == other.ea

  override fun hashCode(): Int {
    var hash = feature
    hash = 31 * hash + minor
    hash = 31 * hash + update
    hash = 31 * hash + build
    hash = 31 * hash + (if (ea) 1231 else 1237)
    return hash
  }

  /**
   * @return feature version string, e.g. **1.8** or **11**
   */
  fun toFeatureString(): String = formatVersionTo(upToFeature = true, upToUpdate = true)

  /**
   * @return feature, minor and update components of the version string, e.g., **1.8.0_242** or **11.0.5**
   */
  fun toFeatureMinorUpdateString(): String = formatVersionTo(upToFeature = false, upToUpdate = true)

  override fun toString(): String = formatVersionTo(upToFeature = false, upToUpdate = false)

  private fun formatVersionTo(upToFeature: Boolean, upToUpdate: Boolean): String {
    val sb = StringBuilder()
    if (feature > 8) {
      sb.append(feature)
      if (!upToFeature) {
        if (minor > 0 || update > 0) sb.append('.').append(minor)
        if (update > 0) sb.append('.').append(update)
        if (!upToUpdate) {
          if (ea) sb.append("-ea")
          if (build > 0) sb.append('+').append(build)
        }
      }
    }
    else {
      sb.append("1.").append(feature)
      if (!upToFeature) {
        if (minor > 0 || update > 0 || ea || build > 0) sb.append('.').append(minor)
        if (update > 0) sb.append('_').append(update)
        if (!upToUpdate) {
          if (ea) sb.append("-ea")
          if (build > 0) sb.append("-b").append(build)
        }
      }
    }
    return sb.toString()
  }

  companion object {
    /**
     * Composes a version object out of given parameters.
     *
     * @throws IllegalArgumentException when any of the numbers is negative
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IllegalArgumentException::class)
    fun compose(feature: Int, minor: Int = 0, update: Int = 0, build: Int = 0, ea: Boolean = false): JavaVersion =
      JavaVersion(feature, minor, update, build, ea)

    @Deprecated(
      "Use CurrentJavaVersion.currentJavaVersion() instead",
      ReplaceWith("com.intellij.util.lang.CurrentJavaVersion.currentJavaVersion()"),
      level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun current(): JavaVersion = currentJavaVersionPlatformSpecific()

    private const val MAX_ACCEPTED_VERSION = 50 // sanity check

    /**
     * Parses a Java version string.
     *
     * Supports various sources, including (but not limited to):<br></br>
     * - `"java.*version"` system properties (a version number without any decoration)<br></br>
     * - values of Java compiler -source/-target/--release options ("$MAJOR", "1.$MAJOR")<br></br>
     * - output of "`java -version`" (usually "java version \"$VERSION\"")<br></br>
     * - a second line of the above command (something like to "Java(TM) SE Runtime Environment (build $VERSION)")<br></br>
     * - output of "`java --full-version`" ("java $VERSION")<br></br>
     * - a line of "release" file ("JAVA_VERSION=\"$VERSION\"")
     *
     * See com.intellij.util.lang.JavaVersionTest for examples.
     *
     * @throws IllegalArgumentException if failed to recognize the number.
     */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun parse(versionString: String): JavaVersion {
      // trimming
      var str = versionString.trim { it <= ' ' }
      val trimmingMap = mapOf( // "substring to detect" to "substring from which to trim"
        "Runtime Environment" to "(build ",
        "OpenJ9" to "version ",
        "GraalVM" to "Java "
      )
      for (keyToDetect in trimmingMap.keys) {
        if (str.contains(keyToDetect)) {
          val p = str.indexOf(trimmingMap[keyToDetect]!!)
          if (p > 0) str = str.substring(p)
        }
      }

      // partitioning
      val numbers = mutableListOf<String>()
      val separators = mutableListOf<String>()
      val length = str.length
      var p = 0
      var number = false
      while (p < length) {
        val start = p
        while (p < length && str[p].isDigit() == number) p++
        val part = str.substring(start, p)
        (if (number) numbers else separators).add(part)
        number = !number
      }

      // parsing
      if (!numbers.isEmpty() && !separators.isEmpty()) {
        try {
          var feature = numbers[0].toInt()
          var minor = 0
          var update = 0
          var build = 0
          var ea = false

          if (feature in 5..<MAX_ACCEPTED_VERSION) {
            // Java 9+; Java 5+ (short format)
            p = 1
            while (p < separators.size && "." == separators[p]) p++
            if (p > 1 && numbers.size > 2) {
              minor = numbers[1].toInt()
              update = numbers[2].toInt()
            }
            if (p < separators.size) {
              val s = separators[p]
              if (!s.isEmpty() && s[0] == '-') {
                ea = startsWithWord(s, "-ea") || startsWithWord(s, "-internal")
                if (p < numbers.size && s[s.length - 1] == '+') {
                  build = numbers[p].toInt()
                }
                p++
              }
              if (build == 0 && p < separators.size && p < numbers.size && "+" == separators[p]) {
                build = numbers[p].toInt()
              }
            }
            return JavaVersion(feature, minor, update, build, ea)
          }
          else if (feature == 1 && numbers.size > 1 && separators.size > 1 && "." == separators[1]) {
            // Java 1.0 .. 1.4; Java 5+ (prefixed format)
            feature = numbers[1].toInt()
            if (feature <= MAX_ACCEPTED_VERSION) {
              if (numbers.size > 2 && separators.size > 2 && "." == separators[2]) {
                minor = numbers[2].toInt()
                if (numbers.size > 3 && separators.size > 3 && "_" == separators[3]) {
                  update = numbers[3].toInt()
                  if (separators.size > 4) {
                    val s = separators[4]
                    if (!s.isEmpty() && s[0] == '-') {
                      ea = startsWithWord(s, "-ea") || startsWithWord(s, "-internal")
                    }
                    p = 4
                    while (p < separators.size && !separators[p].endsWith("-b")) p++
                    if (p < numbers.size) {
                      build = numbers[p].toInt()
                    }
                  }
                }
              }
              return JavaVersion(feature, minor, update, build, ea)
            }
          }
        }
        catch (_: NumberFormatException) { }
      }

      throw IllegalArgumentException(versionString)
    }

    private fun startsWithWord(s: String, word: String): Boolean =
      s.startsWith(word) && (s.length == word.length || !s[word.length].isLetterOrDigit())

    /**
     * A safe version of [.parse] - returns `null` when unable to parse a version string.
     */
    @JvmStatic
    fun tryParse(versionString: String?): JavaVersion? {
      if (versionString != null) {
        try {
          return parse(versionString)
        }
        catch (_: IllegalArgumentException) { }
      }

      return null
    }
  }
}
