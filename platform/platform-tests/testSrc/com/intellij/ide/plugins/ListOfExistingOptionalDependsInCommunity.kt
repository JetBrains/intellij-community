// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

/**
 * It's forbidden to add new `<depends optional=true>` tags in the monorepo because it won't allow generating dependencies automatically,
 * see IJPL-231402 for details.
 * Instead of adding new items here, create plugin content modules with additional dependencies.
 */
val existingOptionalDependsTagInCommunityPlugins = mapOf(
  "com.android.tools.design" to setOf(
    "com.intellij.modules.androidstudio",
  ),
  "org.jetbrains.android" to setOf(
    "com.android.tools.idea.smali",
    "com.intellij.modules.androidstudio",
    "com.intellij.modules.idea",
    "intellij.webp",
  ),
  "org.intellij.groovy" to setOf(
    "org.intellij.intelliLang",
    "com.intellij.modules.structuralsearch",
    "JUnit",
  ),
  "Coverage" to setOf(
    "TestNG-J",
    "JUnit",
  ),
  "JUnit" to setOf(
    "com.intellij.properties",
  ),
  "Git4Idea" to setOf(
    "com.jetbrains.performancePlugin",
  ),
  "TestNG-J" to setOf(
    "org.intellij.intelliLang",
  ),
  "com.intellij.mcpServer" to setOf(
    "org.jetbrains.plugins.terminal",
    "Git4Idea",
  ),
  "com.intellij.ml.local.models" to setOf(
    "com.intellij.java",
  ),
  "com.intellij.tasks" to setOf(
    "com.intellij.java",
    "XPathView",
  ),
  "com.jetbrains.filePrediction" to setOf(
    "com.intellij.java",
    "Git4Idea",
  ),
)