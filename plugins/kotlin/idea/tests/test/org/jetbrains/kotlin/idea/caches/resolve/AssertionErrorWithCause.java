// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve;

public class AssertionErrorWithCause extends AssertionError {
    public AssertionErrorWithCause(String detailMessage, Throwable cause) {
        super(detailMessage);

        initCause(cause);
    }
}
