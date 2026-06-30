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
include(":core")
include(":json")
include(":filewatch")
include(":cli")
include(":thread-view")
include(":container")
include(":prompt-core")
include(":prompt-context")
include(":prompt-ui")
include(":prompt-vcs")
include(":prompt-testrunner")
include(":sessions")
include(":sessions-jbcentral")
include(":settings")
include(":sessions-core")
include(":sessions-cost")
include(":ui")
include(":sessions-actions")
include(":sessions-toolwindow")
include(":sessions-launch-config-backend")
include(":claude-common")
include(":claude-awb")
include(":claude-sessions")
include(":codex-common")
include(":codex-thread-view")
include(":codex-ide")
include(":codex-prompt-suggestions")
include(":codex-sessions")
include(":junie-common")
include(":junie-sessions")
include(":opencode-sessions")
include(":pi-awb")
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
project(":common").projectDir = file("lib-agent/common")
project(":core").projectDir = file("lib-agent/core")
project(":json").projectDir = file("lib-agent/json")
project(":filewatch").projectDir = file("lib-agent/filewatch")
project(":cli").projectDir = file("lib-agent/cli")
project(":sessions-core").projectDir = file("lib-agent/sessions-core")
project(":sessions-launch-config-backend").projectDir = file("sessions-launch-config/backend")
project(":claude-common").projectDir = file("lib-agent/providers/claude/common")
project(":claude-awb").projectDir = file("claude/awb")
project(":claude-sessions").projectDir = file("lib-agent/providers/claude/sessions")
project(":codex-common").projectDir = file("lib-agent/providers/codex/common")
project(":codex-thread-view").projectDir = file("codex/thread-view")
project(":codex-ide").projectDir = file("codex/ide")
project(":codex-prompt-suggestions").projectDir = file("codex/prompt-suggestions")
project(":codex-sessions").projectDir = file("lib-agent/providers/codex/sessions")
project(":junie-common").projectDir = file("lib-agent/providers/junie/common")
project(":junie-sessions").projectDir = file("lib-agent/providers/junie/sessions")
project(":opencode-sessions").projectDir = file("lib-agent/providers/opencode/sessions")
project(":pi-awb").projectDir = file("pi/awb")
project(":pi-sessions").projectDir = file("lib-agent/providers/pi/sessions")
project(":pi-sessions-filewatch").projectDir = file("lib-agent/providers/pi/sessions-filewatch")
project(":terminal-sessions").projectDir = file("lib-agent/providers/terminal/sessions")
