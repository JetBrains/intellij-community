// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.openapi.ui.impl;

import com.intellij.ui.FocusTrackback;

/**
 * @author spleaner
 */
public interface FocusTrackbackProvider {

  FocusTrackback getFocusTrackback();

}
