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
package org.netbeans.lib.cvsclient.file;

import org.netbeans.lib.cvsclient.IConnectionStreams;

import java.io.IOException;
import java.util.Collection;

/**
 * @author  Thomas Singer
 */
public interface ILocalFileReader {

	void transmitTextFile(FileObject fileObject, IConnectionStreams connectionStreams, ICvsFileSystem cvsFileSystem) throws IOException;

	void transmitBinaryFile(FileObject fileObject, IConnectionStreams connectionStreams, ICvsFileSystem cvsFileSystem) throws IOException;

	boolean exists(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem);

	boolean isWritable(FileObject fileObject, ICvsFileSystem cvsFileSystem);

	void listFilesAndDirectories(DirectoryObject directoryObject, Collection<String> fileNames, Collection<String> directoryNames, 
                                     ICvsFileSystem cvsFileSystem);
}
