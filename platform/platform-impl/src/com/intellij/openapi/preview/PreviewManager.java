// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.preview;

import com.intellij.openapi.project.Project;
import com.intellij.util.DeprecatedMethodException;

@Deprecated
public interface PreviewManager {
  final class SERVICE {
    /**
     * @deprecated always return null
     */
    @Deprecated
    public static <V, C> C preview(Project project, PreviewProviderId<V, C> id, V data, boolean requestFocus) {
      DeprecatedMethodException.report("Please don't use; always returns null");
      return null;
    }
  }
}
