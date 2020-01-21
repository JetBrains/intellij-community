// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * @author traff
 */
public abstract class CoverageOptions implements UnnamedConfigurable {
  public static final ProjectExtensionPointName<CoverageOptions> EP_NAME = new ProjectExtensionPointName<>("com.intellij.coverageOptions");
}
