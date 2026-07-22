// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

/**
 * It's forbidden to add new `<depends optional=true>` tags in the monorepo because it won't allow generating dependencies automatically,
 * see IJPL-231402 for details.
 * Instead of adding new items here, create plugin content modules with additional dependencies.
 */
val existingOptionalDependsTagInCommunityPlugins = mapOf(
  "com.android.tools.design" to setOf( // IDEA-391292
    "com.intellij.modules.androidstudio",
  ),
  "org.jetbrains.android" to setOf( // IDEA-391293
    "com.android.tools.idea.smali",
    "com.intellij.modules.androidstudio",
    "com.intellij.modules.idea",
    "intellij.webp",
  ),
  "Coverage" to setOf( // IDEA-391283
    "TestNG-J",
    "JUnit",
  ),
  "com.intellij.tasks" to setOf( // IJPL-249465
    "XPathView",
  ),
  "com.jetbrains.filePrediction" to setOf( // IJPL-249466
    "com.intellij.java",
    "Git4Idea",
  ),
)
