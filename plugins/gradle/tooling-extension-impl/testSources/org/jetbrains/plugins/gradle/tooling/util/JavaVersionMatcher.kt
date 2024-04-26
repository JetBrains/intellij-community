// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util

import com.intellij.util.lang.JavaVersion

object JavaVersionMatcher {

  @JvmStatic
  fun isVersionMatch(sourceVersion: JavaVersion, targetVersionNotation: String): Boolean {
    val (targetJavaVersion, relation) = parseDeclaration(targetVersionNotation) ?: return false
    val baseSourceVersion = sourceVersion.feature
    val baseTargetVersion = targetJavaVersion.feature
    return when (relation) {
      Relation.GREATER_THAN -> baseSourceVersion > baseTargetVersion
      Relation.LESS_THAN -> baseSourceVersion < baseTargetVersion
      Relation.LESS_THAN_OR_EQUAL -> baseSourceVersion <= baseTargetVersion
      Relation.GREATER, Relation.GREATER_THAN_OR_EQUAL -> baseSourceVersion >= baseTargetVersion
      Relation.NOT -> baseSourceVersion != baseTargetVersion
      Relation.EQUAL -> baseSourceVersion == baseTargetVersion
    }
  }

  private fun parseDeclaration(query: String): Pair<JavaVersion, Relation>? {
    val relation = Relation.values()
                     .find { query.startsWith(it.sign) || query.endsWith(it.sign) }
                   ?: Relation.EQUAL
    val javaVersion = query.replace(relation.sign, "").let { JavaVersion.tryParse(it) } ?: return null
    return Pair(javaVersion, relation)
  }

  private enum class Relation(val sign: String) {
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    GREATER("+"),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    NOT("!"),
    EQUAL("==")
  }
}