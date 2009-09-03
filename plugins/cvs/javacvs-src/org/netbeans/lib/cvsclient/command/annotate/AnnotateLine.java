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
package org.netbeans.lib.cvsclient.command.annotate;

import java.util.Date;

/**
 * @author  Thomas Singer
 */
public final class AnnotateLine {

	// Fields =================================================================

	private final String author;
	private final String revision;
	private final Date date;
	private final String content;

	// Setup ==================================================================

	AnnotateLine(String revision, String author, Date date, String content) {
		this.revision = revision;
		this.author = author;
		this.date = date;
		this.content = content;
	}

	// Accessing ==============================================================

	public String getAuthor() {
		return author;
	}

	public String getRevision() {
		return revision;
	}

	public Date getDate() {
		return date;
	}

	public String getContent() {
		return content;
	}
}