/*
 * @author max
 */
package com.intellij.util.io.zip;

import java.util.Date;

public class DosTime {
  private DosTime() {
  }

  /*
   * Converts DOS time to Java time (number of milliseconds since epoch).
   */
  public static long dosToJavaTime(long dtime) {
    Date d = new Date((int)(((dtime >> 25) & 0x7f) + 80), (int)(((dtime >> 21) & 0x0f) - 1), (int)((dtime >> 16) & 0x1f),
                      (int)((dtime >> 11) & 0x1f), (int)((dtime >> 5) & 0x3f), (int)((dtime << 1) & 0x3e));
    return d.getTime();
  }

  /*
   * Converts Java time to DOS time.
   */
  public static long javaToDosTime(long time) {
    Date d = new Date(time);
    int year = d.getYear() + 1900;
    if (year < 1980) {
      return (1 << 21) | (1 << 16);
    }
    return (year - 1980) << 25 |
           (d.getMonth() + 1) << 21 |
           d.getDate() << 16 |
           d.getHours() << 11 |
           d.getMinutes() << 5 |
           d.getSeconds() >> 1;
  }
}
