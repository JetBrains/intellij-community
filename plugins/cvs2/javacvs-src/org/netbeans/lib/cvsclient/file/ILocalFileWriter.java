/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.
 *
 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.file;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.nio.charset.Charset;

/**
 * Handles the reading and writing of files to and from the server. Different
 * implementations of this interface can use different formats for sending or
 * receiving the files, for example gzipped format.
 * @author  Robert Greig
 */
public interface ILocalFileWriter {

	void writeTextFile(FileObject fileObject, int length, InputStream inputStream, boolean readOnly, IReaderFactory readerFactory,
                           IFileReadOnlyHandler fileReadOnlyHandler, IFileSystem fileSystem, @Nullable final Charset charSet) throws IOException;

	void writeBinaryFile(FileObject fileObject, int length, InputStream inputStream, boolean readOnly, IFileReadOnlyHandler fileReadOnlyHandler, ICvsFileSystem cvsFileSystem) throws IOException;

	/**
	 * Remove the specified file from the local disk.
	 */
	void removeLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) throws IOException;

	/**
	 * Rename the local file
	 */
	void renameLocalFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, String newFileName) throws IOException;

	/**
	 * Set the modified date of the next file to be written. The next call
	 * to writeFile will use this date.
	 * @param modifiedDate the date the file should be marked as modified
	 */
	void setNextFileDate(Date modifiedDate);

	void setNextFileMode(String nextFileMode);
}
