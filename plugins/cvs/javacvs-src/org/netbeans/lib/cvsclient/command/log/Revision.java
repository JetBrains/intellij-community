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
package org.netbeans.lib.cvsclient.command.log;

import java.util.Date;

/**
 * @author  Thomas Singer
 */
public final class Revision {

	// Fields =================================================================

	private String number;
	private Date date;
	private String author;
	private String state;
	private String lines;
	private String message;
	private String branches;

	// Setup ==================================================================

	public Revision(String number) {
		this.number = number.intern();
	}

	// Accessing ==============================================================

	public String getNumber() {
		return number;
	}

	public void setNumber(String number) {
		this.number = number.intern();
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
        this.date = date;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author.intern();
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state.intern();
	}

	public String getLines() {
		return lines;
	}

	public void setLines(String lines) {
		this.lines = lines.intern();
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message.intern();
	}

	public String getBranches() {
		return branches;
	}

	public void setBranches(String branches) {
		this.branches = branches.intern();
	}
}
