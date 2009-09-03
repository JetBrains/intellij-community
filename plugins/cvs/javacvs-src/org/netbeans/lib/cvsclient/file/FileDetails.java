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
package org.netbeans.lib.cvsclient.file;

/**
 * A wrapper class that describes a file.
 * @author  Robert Greig
 */
public final class FileDetails {

	// Fields =================================================================

	private final boolean isBinary;
	private final FileObject fileObject;

	// Setup ==================================================================

	public FileDetails(FileObject fileObject, boolean isBinary) {
		this.isBinary = isBinary;
		this.fileObject = fileObject;
	}

	// Accessing ==============================================================

	public FileObject getFileObject() {
		return fileObject;
	}

	/**
	 * Return the file type.
	 * @return true if the file is binary, false if it is text
	 */
	public boolean isBinary() {
		return isBinary;
	}
}