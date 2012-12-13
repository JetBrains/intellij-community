/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DateFormatUtilRt {
  private static final SimpleDateFormat BUILD_DATE_FORMAT = new SimpleDateFormat("dd MMM yyyy HH:ss", Locale.US);

  @NotNull
  public static String formatBuildDate(@NotNull Calendar cal) {
    return BUILD_DATE_FORMAT.format(cal.getTime());
  }
}
