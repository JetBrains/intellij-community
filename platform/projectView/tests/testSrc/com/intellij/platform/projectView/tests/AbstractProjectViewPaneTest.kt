// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

/**
 * Base class for Project View frontend tests.
 *
 * Concrete subclasses must be annotated with `@TestApplication` (that also enables `@TestFixtures`) and
 * typically declare `projectFixture`/`moduleFixture`/`sourceRootFixture`s.
 */
internal abstract class AbstractProjectViewPaneTest
