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
package org.netbeans.lib.cvsclient.event;

import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;

/**
 * @author  Thomas Singer
 */
public interface IEventSender {

	void notifyTerminationListeners(boolean error);

	void notifyMessageListeners(byte[] message, boolean error, boolean tagged);

	void notifyFileInfoListeners(Object fileInfoContainer);

	void notifyFileInfoListeners(byte[] fileInfoContainer);

	void notifyModuleExpansionListeners(String module);

	void notifyEntryListeners(FileObject fileObject, Entry entry);

	void notifyDirectoryListeners(DirectoryObject directoryObject, boolean setStatic);
}
