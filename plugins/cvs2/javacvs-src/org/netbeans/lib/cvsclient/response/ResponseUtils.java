package org.netbeans.lib.cvsclient.response;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author  Thomas Singer
 */
final class ResponseUtils {

	private static SimpleDateFormat dateFormatter;

	private static DateFormat getFormatter() {
		if (dateFormatter == null) {
			dateFormatter = new SimpleDateFormat("dd MMM yy HH:mm:ss", Locale.US);
			dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT+0000"));
		}
		return dateFormatter;
	}

	protected static Date parseDateString(String dateString) throws ParseException {
		return getFormatter().parse(dateString);
	}
}
