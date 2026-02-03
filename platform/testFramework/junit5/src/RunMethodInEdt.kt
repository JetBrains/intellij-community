// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import org.jetbrains.annotations.TestOnly

/**
 * Instructs the framework to run a single method in EDT. [RunInEdt] is required on the class level for this annotation to be picked up.
 * If [writeIntent] is set to [WriteIntentMode.True], then test method will be run with Write Intent Lock.
 * If [writeIntent] is set to [WriteIntentMode.False], then test method will be run without Write Intent Lock.
 * If [writeIntent] is set to [WriteIntentMode.Default] (default), then Write Intent Lock is controlled by [RunInEdt.writeIntent].
 */
@TestOnly
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
annotation class RunMethodInEdt(val writeIntent: WriteIntentMode = WriteIntentMode.Default) {
  /**
   * Enumeration class that represents the mode for the write-intent lock.
   *
   * The WriteIntentMode enum class provides three possible modes:
   * - True: Indicates that the test must be run under Write Intent Lock.
   * - False: Indicates that the test must be run without Write Intent Lock.
   * - Default: Indicates that the write-intent lock is controlled by the parent annotation (see [RunInEdt]).
   *
   * This class is annotated with the @TestOnly annotation, indicating that it is intended
   * for testing purposes only.
   */
  @TestOnly
  enum class WriteIntentMode { True, False, Default }
}
