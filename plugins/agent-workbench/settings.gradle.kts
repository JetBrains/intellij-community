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
include(":prompt-context")
include(":prompt-ui")
include(":prompt-vcs")
include(":prompt-testrunner")
include(":sessions")
include(":sessions-jbcentral")
include(":sessions-core")
include(":sessions-actions")
include(":sessions-toolwindow")
include(":sessions-launch-config-backend")
include(":claude-common")
include(":claude-sessions")
include(":codex-common")
include(":codex-ide")
include(":codex-sessions")
include(":junie-common")
include(":junie-sessions")
include(":pi-sessions")
include(":pi-sessions-filewatch")
include(":terminal-sessions")
include(":ai-review")
include(":ai-review-agents")
include(":ai-review-space")
include(":vcs-merge")

project(":prompt-core").projectDir = file("prompt/core")
project(":prompt-context").projectDir = file("prompt/context")
project(":prompt-ui").projectDir = file("prompt/ui")
project(":prompt-vcs").projectDir = file("prompt/vcs")
project(":prompt-testrunner").projectDir = file("prompt/testrunner")
project(":sessions-launch-config-backend").projectDir = file("sessions-launch-config/backend")
project(":claude-common").projectDir = file("claude/common")
project(":claude-sessions").projectDir = file("claude/sessions")
project(":codex-common").projectDir = file("codex/common")
project(":codex-ide").projectDir = file("codex/ide")
project(":codex-sessions").projectDir = file("codex/sessions")
project(":junie-common").projectDir = file("junie/common")
project(":junie-sessions").projectDir = file("junie/sessions")
project(":pi-sessions").projectDir = file("pi/sessions")
project(":pi-sessions-filewatch").projectDir = file("pi/sessions-filewatch")
project(":terminal-sessions").projectDir = file("terminal/sessions")
