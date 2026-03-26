// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

buildscript {
  repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    gradlePluginPortal()
  }

  pluginManagement {
    repositories {
      maven("https://oss.sonatype.org/content/repositories/snapshots/")
      gradlePluginPortal()
    }
    plugins {
      id("java")
      id("org.jetbrains.kotlin.jvm") version "2.2.20"
      kotlin("plugin.serialization") version "2.2.20"
      id("org.jetbrains.intellij.platform") version "2.9.0"
      id("org.jetbrains.intellij.platform.settings") version "2.9.0"
      id("org.jetbrains.intellij.platform.module") version "2.9.0"
    }
  }
}

rootProject.name = "agent-workbench"

include(":common")
include(":json")
include(":filewatch")
include(":chat")
include(":container")
include(":prompt-core")
include(":prompt-ui")
include(":prompt-vcs")
include(":prompt-testrunner")
include(":sessions")
include(":sessions-core")
include(":sessions-actions")
include(":sessions-toolwindow")
include(":sessions-launch-config-backend")
include(":claude-common")
include(":claude-sessions")
include(":codex-common")
include(":codex-sessions")

project(":prompt-core").projectDir = file("prompt/core")
project(":prompt-ui").projectDir = file("prompt/ui")
project(":prompt-vcs").projectDir = file("prompt/vcs")
project(":prompt-testrunner").projectDir = file("prompt/testrunner")
project(":sessions-launch-config-backend").projectDir = file("sessions-launch-config/backend")
project(":claude-common").projectDir = file("claude/common")
project(":claude-sessions").projectDir = file("claude/sessions")
project(":codex-common").projectDir = file("codex/common")
project(":codex-sessions").projectDir = file("codex/sessions")
