// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.impl

/**
 * Resources with lifetime [Level.METHOD] are created before and destroyed after each method.
 * [ResourceExtensionImpl] implements both [org.junit.jupiter.api.extension.BeforeAllCallback] and
 * [org.junit.jupiter.api.extension.BeforeEachCallback], but when registered as instance field (hence, [Level.METHOD])
 * [org.junit.jupiter.api.extension.BeforeAllCallback] isn't called.
 *
 * This class guesses level by checking which method gets called first
 */
internal class ResourceLifetime {

  @Volatile
  private var currentLevel: Level? = null


  val classLevel: Boolean get() = level(Level.CLASS)
  val methodLevel: Boolean get() = level(Level.METHOD)


  private fun level(requiredLevel: Level): Boolean {
    currentLevel?.let {
      return it == requiredLevel
    }
    currentLevel = requiredLevel
    return true
  }


  private enum class Level {
    CLASS,
    METHOD
  }
}