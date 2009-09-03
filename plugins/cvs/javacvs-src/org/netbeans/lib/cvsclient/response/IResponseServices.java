/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/

 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.

 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.response;

import org.netbeans.lib.cvsclient.event.IEventSender;

import java.util.Date;

/**
 * Services that are provided to response handlers.
 * @author  Robert Greig
 */
public interface IResponseServices {
	/**
	 * Set the modified date of the next file to be written. The next call
	 * to writeFile will use this date.
	 * @param modifiedDate the date the file should be marked as modified
	 */
	void setNextFileDate(Date modifiedDate);

	/**
	 * Get the modified date of the next file to be written. This will also
	 * null any stored date so that future calls will not retrieve a date
	 * that was meant for a previous file.
	 * @return the date the next file should be marked as having been modified
	 * on.
	 */
	Date getNextFileDate();

	/**
	 * Get the CVS event manager. This is generally called by response handlers
	 * that want to fire events.
	 * @return the eventManager
	 */
	IEventSender getEventSender();

	void setValidRequests(String validRequests);

	String getNextFileMode();

	void setNextFileMode(String nextFileMode);
}
