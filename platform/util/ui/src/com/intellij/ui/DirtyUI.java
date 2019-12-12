// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import java.lang.annotation.*;

/**
 * <p>
 * This annotation specifies code which runs on Swing Event Dispatch Thread and accesses IDE model (PSI, etc.)
 * at the same time.
 * <p>
 * Accessing IDE model from EDT is prohibited by default, but many existing components are designed without
 * such limitations. Such code can be marked with this annotation which will cause a dedicated instrumenter
 * to modify bytecode to acquire Write Intent lock before the execution and release after the execution.
 * <p>
 * Marked methods will be modified to acquire/release IW lock. Marked classes will have a predefined set of their methods
 * modified in the same way. This list of methods can be found at {@link com.intellij.ide.instrument.LockWrappingClassVisitor#METHODS_TO_WRAP}
 *
 * @see com.intellij.ide.instrument.WriteIntentLockInstrumenter
 * @see com.intellij.openapi.application.Application
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface DirtyUI {
}
