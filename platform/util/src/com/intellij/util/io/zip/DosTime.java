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

/*
 * @author max
 */
package com.intellij.util.io.zip;

import java.util.Calendar;

public class DosTime {
  private DosTime() {
  }

  /*
   * Converts DOS time to Java time (number of milliseconds since epoch).
   */
  public static long dosToJavaTime(long dtime) {
    Calendar cal = Calendar.getInstance();
    cal.set((int)(((dtime >> 25) & 0x7f) + 1980), (int)(((dtime >> 21) & 0x0f) - 1), (int)((dtime >> 16) & 0x1f),
            (int)((dtime >> 11) & 0x1f), (int)((dtime >> 5) & 0x3f), (int)((dtime << 1) & 0x3e));
    cal.clear(Calendar.MILLISECOND);
    return cal.getTimeInMillis();
  }

  /*
   * Converts Java time to DOS time.
   */
  public static long javaToDosTime(long time) {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(time);
    int year = cal.get(Calendar.YEAR);
    if (year < 1980) {
      return (1 << 21) | (1 << 16);
    }
    return (year - 1980) << 25
           | (cal.get(Calendar.MONTH) + 1) << 21
           | cal.get(Calendar.DAY_OF_MONTH) << 16
           | cal.get(Calendar.HOUR_OF_DAY) << 11
           | cal.get(Calendar.MINUTE) << 5
           | cal.get(Calendar.SECOND) >> 1;
  }
}
