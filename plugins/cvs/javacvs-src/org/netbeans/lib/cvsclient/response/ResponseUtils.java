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
      dateFormatter = new SyncDateFormat(new SimpleDateFormat(DATE_FORMAT_STR, Locale.US));
      dateFormatter.setTimeZone(TimeZone.getTimeZone(TIME_ZONE_FORMAT_STR));
    }
    return dateFormatter;
  }

  protected static Date parseDateString(String dateString) throws ParseException {
    return getFormatter().parse(dateString);
  }
}
