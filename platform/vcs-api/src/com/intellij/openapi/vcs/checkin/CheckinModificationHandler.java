// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

/**
 * Marker-interface for {@link CheckinHandler} that notifies that check will perform mutable operations on committed changes.
 * <p>
 * Such handlers shall be processed before other checks during the commit.
 */
public interface CheckinModificationHandler {
}