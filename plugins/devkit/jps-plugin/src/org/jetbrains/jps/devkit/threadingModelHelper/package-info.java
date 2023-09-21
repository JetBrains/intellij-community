// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * <h2>Threading Model Helper</h2>
 * <p>
 * Instruments methods annotated with
 * <ul>
 * <li>{@code @RequiresEdt}
 * <li>{@code @RequiresBackgroundThread}
 * <li>{@code @RequiresReadLock}
 * <li>{@code @RequiresWriteLock}
 * <li>{@code @RequiresReadLockAbsence}
 * </ul>
 * by inserting
 * <ul>
 * <li>{@code ThreadingAssertions.assertEventDispatchThread()}
 * <li>{@code Application.assertIsNonDispatchThread()}
 * <li>{@code Application.assertReadAccessAllowed()}
 * <li>{@code Application.assertWriteAccessAllowed()}
 * <li>{@code Application.assertReadAccessNotAllowed()}
 * </ul>
 * calls accordingly.
 * <p>
 * To disable the instrumentation, use the {@code tmh.generate.assertions.for.annotations} key in the Registry.
 *
 * <h3>Limitations</h3>
 *
 * <ul>
 * <li>Only Java code is instrumented,
 * <a href="https://youtrack.jetbrains.com/issue/IDEA-263465">Kotlin instrumentation is planned</a>.
 * </ul>
 */
package org.jetbrains.jps.devkit.threadingModelHelper;