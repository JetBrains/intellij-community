// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

/**
 * These descriptors already have a `package` attribute. Do not add new entries.
 * Remove each entry when you remove its attribute.
 */
val existingPackageAttributesInCommunity: Map<String, Set<String>> = mapOf(
  "intellij.android.gradle.declarative.lang" to setOf(
    "android/gradle-declarative-lang/resources/intellij.android.gradle.declarative.lang.xml",
  ),
  "intellij.android.gradle.dsl" to setOf("android/gradle-dsl/resources/META-INF/plugin.xml"),
  "intellij.android.gradle.dsl.android" to setOf("android/gradle-dsl-android/resources/intellij.android.gradle.dsl.android.xml"),
  "intellij.android.gradle.dsl.declarative" to setOf(
    "android/gradle-dsl-declarative/resources/intellij.android.gradle.dsl.declarative.xml",
  ),
  "intellij.android.gradle.dsl.flags" to setOf("android/gradle-dsl-flags/resources/intellij.android.gradle.dsl.flags.xml"),
  "intellij.android.gradle.dsl.groovy" to setOf(
    "android/gradle-dsl-groovy/resources/intellij.android.gradle.dsl.groovy.xml",
  ),
  "intellij.android.gradle.dsl.kotlin" to setOf(
    "android/gradle-dsl-kotlin/resources/intellij.android.gradle.dsl.kotlin.xml",
  ),
  "intellij.android.gradle.dsl.toml" to setOf("android/gradle-dsl-toml/resources/intellij.android.gradle.dsl.toml.xml"),
  "intellij.commandInterface" to setOf("commandInterface/resources/intellij.commandInterface.xml"),
  "intellij.completionMlRanking" to setOf("plugins/completion-ml-ranking/resources/META-INF/plugin.xml"),
  "intellij.compose.ide.plugin" to setOf("plugins/compose/resources/META-INF/plugin.xml"),
  "intellij.compose.ide.plugin.codeInsight" to setOf(
    "plugins/compose/intellij.compose.ide.plugin.codeInsight/resources/intellij.compose.ide.plugin.codeInsight.xml",
  ),
  "intellij.compose.ide.plugin.resources" to setOf(
    "plugins/compose/intellij.compose.ide.plugin.resources/resources/intellij.compose.ide.plugin.resources.xml",
  ),
  "intellij.compose.ide.plugin.resources.android" to setOf(
    "plugins/compose/intellij.compose.ide.plugin.resources.android/resources/intellij.compose.ide.plugin.resources.android.xml",
  ),
  "intellij.copyright.vcs" to setOf("plugins/copyright/vcs/resources/intellij.copyright.vcs.xml"),
  "intellij.dev.codeInsight" to setOf("plugins/dev/intellij.dev.codeInsight/resources/intellij.dev.codeInsight.xml"),
  "intellij.dev.psiViewer" to setOf("plugins/dev/intellij.dev.psiViewer/resources/intellij.dev.psiViewer.xml"),
  "intellij.dotenv" to setOf("plugins/env-files-support/src/main/resources/META-INF/plugin.xml"),
  "intellij.dotenv.java" to setOf("plugins/env-files-support/java/resources/intellij.dotenv.java.xml"),
  "intellij.dotenv.kotlin" to setOf("plugins/env-files-support/kotlin/resources/intellij.dotenv.kotlin.xml"),
  "intellij.dotenv.python" to setOf("plugins/env-files-support/python/resources/intellij.dotenv.python.xml"),
  "intellij.execution.process.elevation" to setOf(
    "platform/execution-process-elevation/resources/intellij.execution.process.elevation.xml",
  ),
  "intellij.execution.process.mediator.client" to setOf(
    "platform/execution-process-mediator/client/resources/intellij.execution.process.mediator.client.xml",
  ),
  "intellij.execution.process.mediator.daemon" to setOf(
    "platform/execution-process-mediator/daemon/resources/intellij.execution.process.mediator.daemon.xml",
  ),
  "intellij.featuresTrainer" to setOf("plugins/ide-features-trainer/res/META-INF/plugin.xml"),
  "intellij.featuresTrainer.onboarding" to setOf(
    "plugins/ide-features-trainer/onboarding/resources/intellij.featuresTrainer.onboarding.xml",
  ),
  "intellij.groovy.git" to setOf("plugins/groovy/git/resources/intellij.groovy.git.xml"),
  "intellij.ide.startup.importSettings" to setOf("plugins/ide-startup/importSettings/resources/intellij.ide.startup.importSettings.xml"),
  "intellij.java.dev" to setOf("plugins/dev/intellij.java.dev/resources/intellij.java.dev.xml"),
  "intellij.javaFX.properties" to setOf("plugins/javaFX/properties/resources/intellij.javaFX.properties.xml"),
  "intellij.kotlin.dev" to setOf("plugins/dev/intellij.kotlin.dev/resources/intellij.kotlin.dev.xml"),
  "intellij.platform.bookmarks.backend" to setOf("platform/bookmarks/backend/resources/intellij.platform.bookmarks.backend.xml"),
  "intellij.platform.bookmarks.frontend" to setOf("platform/bookmarks/frontend/resources/intellij.platform.bookmarks.frontend.xml"),
  "intellij.platform.debugger.impl.backend" to setOf(
    "platform/xdebugger-impl/backend/resources/intellij.platform.debugger.impl.backend.xml",
  ),
  "intellij.platform.debugger.impl.bookmarks" to setOf(
    "platform/xdebugger-impl/bookmarks/resources/intellij.platform.debugger.impl.bookmarks.xml",
  ),
  "intellij.platform.diagnostic.telemetry.agent.extension" to setOf(
    "platform/diagnostic/telemetry/agent-extension/resources/intellij.platform.diagnostic.telemetry.agent.extension.xml",
  ),
  "intellij.platform.frontend" to setOf("platform/platform-frontend/resources/intellij.platform.frontend.xml"),
  "intellij.platform.ide.newUsersOnboarding" to setOf(
    "platform/new-users-onboarding/resources/intellij.platform.ide.newUsersOnboarding.xml",
  ),
  "intellij.platform.ide.nonModalWelcomeScreen" to setOf(
    "platform/non-modal-welcome-screen/resources/intellij.platform.ide.nonModalWelcomeScreen.xml",
  ),
  "intellij.platform.inline.completion" to setOf("platform/inline-completion/shared/resources/intellij.platform.inline.completion.xml"),
  "intellij.platform.ml.logs" to setOf("platform/ml-logs/resources/intellij.platform.ml.logs.xml"),
  "intellij.platform.pluginManager.shared" to setOf("platform/pluginManager/shared/resources/intellij.platform.pluginManager.shared.xml"),
  "intellij.platform.progress.backend" to setOf("platform/progress/backend/resources/intellij.platform.progress.backend.xml"),
  "intellij.platform.project.backend" to setOf("platform/project/backend/resources/intellij.platform.project.backend.xml"),
  "intellij.platform.searchEverywhere.backend" to setOf(
    "platform/searchEverywhere/backend/resources/intellij.platform.searchEverywhere.backend.xml",
  ),
  "intellij.platform.searchEverywhere.frontend" to setOf(
    "platform/searchEverywhere/frontend/resources/intellij.platform.searchEverywhere.frontend.xml",
  ),
  "intellij.platform.searchEverywhere.testFramework" to setOf(
    "platform/searchEverywhere/testFramework/resources/intellij.platform.searchEverywhere.testFramework.xml",
  ),
  "intellij.platform.smRunner.vcs" to setOf("platform/smRunner/vcs/resources/intellij.platform.smRunner.vcs.xml"),
  "intellij.platform.warmup.performanceTesting" to setOf(
    "platform/warmup/performanceTesting/resources/intellij.platform.warmup.performanceTesting.xml",
  ),
  "intellij.platform.whatsNew" to setOf("platform/whatsNew/resources/intellij.platform.whatsNew.xml"),
  "intellij.settingsSync.core" to setOf("platform/settings-sync-core/resources/intellij.settingsSync.core.xml"),
  "intellij.tools.ide.starter.bus" to setOf("plugins/performanceTesting/event-bus/resources/intellij.tools.ide.starter.bus.xml"),
  "intellij.vcs.git.backend" to setOf("plugins/git4idea/backend/resources/intellij.vcs.git.backend.xml"),
  "intellij.vcs.git.featuresTrainer" to setOf("plugins/git-features-trainer/resources/intellij.vcs.git.featuresTrainer.xml"),
)
