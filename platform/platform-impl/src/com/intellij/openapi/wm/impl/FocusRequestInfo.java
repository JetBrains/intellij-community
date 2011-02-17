/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl;

import com.intellij.util.ExceptionUtil;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Konstantin Bulenkov
 */
public final class FocusRequestInfo {
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
  private final String when;
  private final Throwable trace;
  private final boolean forced;
  private Component component;

  public FocusRequestInfo(Component c, Throwable trace, boolean forced) {
    this.forced = forced;
    this.trace = trace;
    when = DATE_FORMAT.format(new Date());
    component = c;
  }

  public String getStackTrace() {
    return ExceptionUtil.getThrowableText(trace);
  }

  public boolean isForced() {
    return forced;
  }

  public String getDate() {
    return when;
  }

  public Component getComponent() {
    return component;
  }
}
