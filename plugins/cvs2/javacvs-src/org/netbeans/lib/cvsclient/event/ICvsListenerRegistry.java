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

/**
 * @author  Thomas Singer
 */
public interface ICvsListenerRegistry {

	void addTerminationListener(ITerminationListener listener);

	void removeTerminationListener(ITerminationListener listener);

	void addMessageListener(IMessageListener listener);

	void removeMessageListener(IMessageListener listener);

	void addModuleExpansionListener(IModuleExpansionListener listener);

	void removeModuleExpansionListener(IModuleExpansionListener listener);

	void addEntryListener(IEntryListener listener);

	void removeEntryListener(IEntryListener listener);

	void addFileInfoListener(IFileInfoListener listener);

	void removeFileInfoListener(IFileInfoListener listener);

	void addDirectoryListener(IDirectoryListener listener);

	void removeDirectoryListener(IDirectoryListener listener);
}
