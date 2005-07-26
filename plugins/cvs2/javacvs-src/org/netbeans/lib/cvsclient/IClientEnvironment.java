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

import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.file.IFileReadOnlyHandler;
import org.netbeans.lib.cvsclient.file.ILocalFileReader;
import org.netbeans.lib.cvsclient.file.ILocalFileWriter;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

/**
 * @author  Thomas Singer
 */
public interface IClientEnvironment {

	IConnection getConnection();

	ICvsFileSystem getCvsFileSystem();

	CvsRoot getCvsRoot();

	IAdminReader getAdminReader();

	IAdminWriter getAdminWriter();

	ILocalFileReader getLocalFileReader();

	ILocalFileWriter getLocalFileWriter();

	IIgnoreFileFilter getIgnoreFileFilter();

	IFileReadOnlyHandler getFileReadOnlyHandler();

	String getCharset();
}
