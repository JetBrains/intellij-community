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
package org.netbeans.lib.cvsclient.command.reservedcheckout;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author  Thomas Singer
 */
public final class EditorsFileInfoContainer {

	// Fields =================================================================

	private final File file;
	private final List<Editor> editors = new ArrayList<>();

	// Setup ==================================================================

	EditorsFileInfoContainer(File file) {
		this.file = file;
	}

	// Implemented ============================================================

	public File getFile() {
		return file;
	}

	// Accessing ==============================================================

	public final void addEditor(Date date, String user, String client, String editDirectory) {
		editors.add(new Editor(date, user, client, editDirectory));
	}

	public List<Editor> getEditors() {
		return Collections.unmodifiableList(editors);
	}

	// Inner classes ==========================================================

	public static final class Editor {

		private final Date date;
		private final String user;
		private final String client;
		private final String editDirectory;

		private Editor(Date date, String user, String client, String editDirectory) {
			this.date = date;
			this.user = user;
			this.client = client;
			this.editDirectory = editDirectory;
		}

		public String getClient() {
			return client;
		}

		public Date getDate() {
			return date;
		}

		public String getUser() {
			return user;
		}

		public String getEditDirectory() {
			return editDirectory;
		}
	}
}
