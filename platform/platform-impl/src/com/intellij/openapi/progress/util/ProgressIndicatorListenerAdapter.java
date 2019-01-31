// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

/**
 * @author lex
 */
public class ProgressIndicatorListenerAdapter implements ProgressIndicatorListener {
  /**
   * should return whether to stop processing
   */
  @Override
  public void cancelled() {
  }

  /**
   * should return whether to stop processing
   */
  @Override
  public void stopped() {
  }
}
