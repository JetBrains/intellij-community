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

import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;

/**
 * Handles the maintaining and reading of administration information on the
 * local machine. The standard CVS client does this by putting various files in
 * a CVS directory underneath each checked-out directory. How the files are
 * laid out and managed is not specified by the protocol document. <P>Hence it
 * is envisaged that, eventually, a client could add additional files for
 * higher performance or even change the mechanism for storing the information
 * completely.
 * @author  Robert Greig
 */
public interface IAdminReader {

	/**
	 * Get the Entry for the specified file, if one exists
	 */
	Entry getEntry(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) throws IOException;

	/**
	 * Get the entries for a specified directory.
	 * @return an iterator of Entry objects
	 */
	Collection<Entry> getEntries(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) throws IOException;

	/**
	 * Get the repository path for a given directory, for example in
	 * the directory /home/project/foo/bar, the repository directory
	 * might be /usr/cvs/foo/bar. The repository directory is commonly
	 * stored in the file <pre>Repository</pre> in the CVS directory on
	 * the client. (This is the case in the standard CVS command-line tool)
	 * @param repository repository path on the server, e.g. /home/bob/cvs. Must not
	 * end with a slash.
	 */
	String getRepositoryForDirectory(DirectoryObject directoryObject, String repository, ICvsFileSystem cvsFileSystem) throws IOException;

	String getStickyTagForDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem);

	boolean hasCvsDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem);

	boolean isModified(FileObject fileObject, Date entryLastModified, ICvsFileSystem cvsFileSystem);

    boolean isStatic(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem);
}
