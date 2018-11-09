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
package org.netbeans.lib.cvsclient;

import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.response.IResponseServices;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.util.Date;

/**
 * @author  Thomas Singer
 */
public final class ResponseService
        implements IResponseServices {

	// Fields =================================================================

	private final IEventSender eventSender;
	private String validRequests;
	private Date nextFileDate;
	private String nextFileMode;

	// Setup ==================================================================

	public ResponseService(IEventSender eventSender) {
		BugLog.getInstance().assertNotNull(eventSender);

		this.eventSender = eventSender;
	}

	// Implemented ============================================================

	@Override
        public void setNextFileDate(Date nextFileDate) {
		this.nextFileDate = nextFileDate;
	}

	@Override
        public Date getNextFileDate() {
		// We null the instance variable so that future calls will not
		// retrieve a date specified for a previous file
		final Date nextFileDate = this.nextFileDate;
		this.nextFileDate = null;
		return nextFileDate;
	}

	@Override
        public IEventSender getEventSender() {
		return eventSender;
	}

	@Override
        public void setValidRequests(String validRequests) {
		this.validRequests = validRequests;
	}

	@Override
        public String getNextFileMode() {
		final String result = nextFileMode;
		nextFileMode = null;
		return result;
	}

	@Override
        public void setNextFileMode(String nextFileMode) {
		this.nextFileMode = nextFileMode;
	}

	// Accessing ==============================================================

	public String getValidRequests() {
		return validRequests;
	}
}
