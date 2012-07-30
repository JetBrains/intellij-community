/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.logcat;

import com.intellij.execution.ui.ConsoleViewContentType;
import static com.intellij.execution.ui.ConsoleViewContentType.registerNewConsoleViewType;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Key;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 3, 2009
 * Time: 8:41:27 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidLogcatConstants {
  public static final TextAttributesKey VERBOSE_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("LOGCAT_VERBOSE_OUTPUT", ConsoleViewContentType.NORMAL_OUTPUT_KEY.getDefaultAttributes());

  public static final TextAttributesKey DEBUG_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("LOGCAT_DEBUG_OUTPUT", ConsoleViewContentType.NORMAL_OUTPUT_KEY.getDefaultAttributes());

  public static final TextAttributesKey INFO_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("LOGCAT_INFO_OUTPUT", ConsoleViewContentType.NORMAL_OUTPUT_KEY.getDefaultAttributes());

  public static final TextAttributesKey WARNING_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("LOGCAT_WARNING_OUTPUT", ConsoleViewContentType.SYSTEM_OUTPUT_KEY.getDefaultAttributes());

  public static final TextAttributesKey ERROR_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("LOGCAT_ERROR_OUTPUT", ConsoleViewContentType.ERROR_OUTPUT_KEY.getDefaultAttributes());

  public static final TextAttributesKey ASSERT_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("LOGCAT_ASSERT_OUTPUT", ConsoleViewContentType.ERROR_OUTPUT_KEY.getDefaultAttributes());

  public static final Key VERBOSE = new Key("verbose.level.title");
  public static final Key DEBUG = new Key("debug.level.title");
  public static final Key INFO = new Key("info.level.title");
  public static final Key WARNING = new Key("warning.level.title");
  public static final Key ERROR = new Key("error.level.title");
  public static final Key ASSERT = new Key("assert.level.title");

  static {
    registerNewConsoleViewType(VERBOSE, new ConsoleViewContentType(VERBOSE.toString(), VERBOSE_OUTPUT_KEY));
    registerNewConsoleViewType(INFO, new ConsoleViewContentType(INFO.toString(), INFO_OUTPUT_KEY));
    registerNewConsoleViewType(WARNING, new ConsoleViewContentType(WARNING.toString(), WARNING_OUTPUT_KEY));
    registerNewConsoleViewType(DEBUG, new ConsoleViewContentType(DEBUG.toString(), DEBUG_OUTPUT_KEY));
    registerNewConsoleViewType(ERROR, new ConsoleViewContentType(ERROR.toString(), ERROR_OUTPUT_KEY));
    registerNewConsoleViewType(ASSERT, new ConsoleViewContentType(ASSERT.toString(), ASSERT_OUTPUT_KEY));
  }

  private AndroidLogcatConstants() {
  }
}
