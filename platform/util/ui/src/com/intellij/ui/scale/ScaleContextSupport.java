// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

/**
 * Support for {@link ScaleContext} (device scale dependent).
 */
public class ScaleContextSupport extends AbstractScaleContextAware<ScaleContext> {
  public ScaleContextSupport() {
    super(ScaleContext.create());
  }
}
