// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

/**
 * Support for {@link UserScaleContext} (device scale independent).
 */
public class UserScaleContextSupport extends AbstractScaleContextAware<UserScaleContext> {
  public UserScaleContextSupport() {
    super(UserScaleContext.create());
  }
}
