/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author max
 */
public class SyncDateFormat {
  private final DateFormat myDelegate;

  public SyncDateFormat(DateFormat delegate) {
    myDelegate = delegate;
  }

  public synchronized Date parse(String s) throws ParseException {
    return myDelegate.parse(s);
  }

  public synchronized String format(Date date) {
    return myDelegate.format(date);
  }

  public synchronized String format(long time) {
    return myDelegate.format(time);
  }

  public synchronized void setTimeZone(final TimeZone timeZone) {
    myDelegate.setTimeZone(timeZone);
  }

  public DateFormat getDelegate() {
    return myDelegate;
  }

  public synchronized String toPattern() {
    if (myDelegate instanceof SimpleDateFormat) {
      return ((SimpleDateFormat)myDelegate).toPattern();
    }
    throw new UnsupportedOperationException("Delegate must be of SimpleDateFormat type");
  }
}
