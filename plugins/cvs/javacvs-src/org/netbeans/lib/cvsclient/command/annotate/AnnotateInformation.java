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

import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author  Thomas Singer
 */
public final class AnnotateInformation {

	// Fields =================================================================

	private final List<AnnotateLine> lines = new ArrayList<>(1000);
	private final File file;

	// Setup ==================================================================

	public AnnotateInformation(File file) {
		BugLog.getInstance().assertNotNull(file);

		this.file = file;
	}

	// Implemented ============================================================

	@SuppressWarnings({"HardCodedStringLiteral"})
        public String toString() {
		return "\nFile: " + file.getAbsolutePath();
	}

	// Accessing ==============================================================

	public File getFile() {
		return file;
	}

	public List<AnnotateLine> getLines() {
		return lines;
	}

	public void addLine(AnnotateLine annotateLine) {
		lines.add(annotateLine);
	}
}
