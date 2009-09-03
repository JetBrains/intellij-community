/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Thomas Singer.
 * Portions created by Thomas Singer are Copyright (C) 2001.
 * All Rights Reserved.
 *
 * Contributor(s): Thomas Singer.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.file;

import java.io.File;
import java.io.IOException;

/**
 * Preprocesses the text file before transmitting to the server.
 * @author  Thomas Singer
 */
public interface ISendTextFilePreprocessor {
	/**
	 * Generates the preprocessed text file from the original text file.
	 */
	File getPreprocessedTextFile(File originalTextFile, IWriterFactory writerFactory) throws IOException;

	/**
	 * Cleans up the preprocessed text file after sending it.
	 */
	void cleanup(File preprocessedTextFile);
}
