// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data

enum class GHReactionContent {
  // Represents the `:+1:` emoji
  THUMBS_UP,

  // Represents the `:-1:` emoji
  THUMBS_DOWN,

  // Represents the `:laugh:` emoji
  LAUGH,

  // Represents the `:hooray:` emoji
  HOORAY,

  // Represents the `:confused:` emoji
  CONFUSED,

  // Represents the `:heart:` emoji
  HEART,

  // Represents the `:rocket:` emoji
  ROCKET,

  // Represents the `:eyes:` emoji
  EYES
}

val GHReactionContent.presentableName
  get() = when(this) {
    GHReactionContent.THUMBS_UP -> "thumbs up"
    GHReactionContent.THUMBS_DOWN -> "thumbs down"
    GHReactionContent.LAUGH -> "laugh"
    GHReactionContent.HOORAY -> "hooray"
    GHReactionContent.CONFUSED -> "confused"
    GHReactionContent.HEART -> "heart"
    GHReactionContent.ROCKET -> "rocket"
    GHReactionContent.EYES -> "eyes"
  }