// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.netbeans.lib.cvsclient.response;

import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.annotations.NonNls;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author Thomas Singer
 */
final class ResponseUtils {

  private static SyncDateFormat dateFormatter;
  @NonNls private static final String DATE_FORMAT_STR = "dd MMM yy HH:mm:ss";
  @NonNls private static final String TIME_ZONE_FORMAT_STR = "GMT+0000";

  private static SyncDateFormat getFormatter() {
    if (dateFormatter == null) {
      SimpleDateFormat delegate = new SimpleDateFormat(DATE_FORMAT_STR, Locale.US);
      delegate.setTimeZone(TimeZone.getTimeZone(TIME_ZONE_FORMAT_STR));
      dateFormatter = new SyncDateFormat(delegate);
    }
    return dateFormatter;
  }

  protected static Date parseDateString(String dateString) throws ParseException {
    return getFormatter().parse(dateString);
  }
}
