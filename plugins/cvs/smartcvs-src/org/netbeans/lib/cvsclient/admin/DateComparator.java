/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient.admin;

/**
 * A class for comparing the file's date and the CVS/Entries date.
 *
 * @author  Thomas Singer
 * @version Sep 29, 2001
 */
public final class DateComparator {

	private static final long SECONDS_PER_HOUR = 3600;

	private static final DateComparator singleton = new DateComparator();

	/**
	 * Returns an instance of a DateComparator.
	 */
	public static DateComparator getInstance() {
		return singleton;
	}

	/**
	 * This class is a singleton. There is no need to subclass or
	 * instantiate it outside.
	 */
	private DateComparator() {
	}

	/**
	 * Compares the specified dates, whether they should be treated as equal.
	 * Returns true to indicate equality.
	 */
	public boolean equals(final long fileTime, final long entryTime) {
		final long fileTimeSeconds = fileTime / 1000;
		final long entryTimeSeconds = entryTime / 1000;
		final long difference = Math.abs(fileTimeSeconds - entryTimeSeconds);
		// differences smaller than 3 seconds are treated as equal
		if (difference < 3) {
			return true;
		}

		// 1-hour-differences (caused by daylight-saving) are treated as equal
		if (difference >= SECONDS_PER_HOUR - 3
		        && difference <= SECONDS_PER_HOUR + 3) {
			return true;
		}
		return false;
	}
}
