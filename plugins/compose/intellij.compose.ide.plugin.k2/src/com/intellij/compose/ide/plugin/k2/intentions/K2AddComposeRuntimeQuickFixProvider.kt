// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.intentions

import com.intellij.compose.ide.plugin.shared.COMPOSABLE_ANNOTATION_FQ_NAME
import com.intellij.compose.ide.plugin.shared.COMPOSE_RUNTIME_ARTIFACT_ID
import com.intellij.compose.ide.plugin.shared.COMPOSE_RUNTIME_GROUP_ID
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.libraries.AddKotlinLibraryQuickFixProvider
import org.jetbrains.kotlin.idea.codeInsight.inspections.libraries.default
import org.jetbrains.kotlin.idea.codeInsight.inspections.libraries.knownClassFqn
import org.jetbrains.kotlin.idea.codeInsight.inspections.libraries.knownNames

internal class K2AddComposeRuntimeQuickFixProvider : AddKotlinLibraryQuickFixProvider(
  libraryGroupId = COMPOSE_RUNTIME_GROUP_ID,
  libraryArtifactId = COMPOSE_RUNTIME_ARTIFACT_ID,
  libraryDescriptorProvider = LibraryDescriptorProvider.default(),
  libraryAvailabilityTester = LibraryAvailabilityTester.knownClassFqn(COMPOSABLE_ANNOTATION_FQ_NAME.asString()),
  libraryReferenceTester = LibraryReferenceTester.knownNames(
    "Composable", "remember", "mutableStateOf", "mutableStateListOf", "mutableStateMapOf", "MutableState",
    "mutableIntStateOf", "mutableFloatStateOf", "mutableLongStateOf", "mutableDoubleStateOf",
    "produceState", "snapshotFlow", "rememberUpdatedState", "collectAsState", "derivedStateOf",
    "LaunchedEffect", "DisposableEffect", "SideEffect", "CompositionLocalProvider",
    "compositionLocalOf", "staticCompositionLocalOf", "rememberCoroutineScope",
  ),
  quickFixText = ComposeIdeBundle.message("compose.intention.add.runtime.fix.text"),
)
