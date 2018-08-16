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
package org.netbeans.lib.cvsclient.command;

import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEntryListener;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.File;

/**
 * @author  Thomas Singer
 */
public class DefaultEntryParser
        implements ICvsListener, IEntryListener {

	// Fields =================================================================

	private final IEventSender eventManager;
	private final ICvsFileSystem cvsFileSystem;

	// Setup ==================================================================

	public DefaultEntryParser(IEventSender eventManager, ICvsFileSystem cvsFileSystem) {
		BugLog.getInstance().assertNotNull(eventManager);
		BugLog.getInstance().assertNotNull(cvsFileSystem);

		this.eventManager = eventManager;
		this.cvsFileSystem = cvsFileSystem;
	}

	// Implemented ============================================================

	@Override
        public void registerListeners(ICvsListenerRegistry listenerRegistry) {
		listenerRegistry.addEntryListener(this);
	}

	@Override
        public void unregisterListeners(ICvsListenerRegistry listenerRegistry) {
		listenerRegistry.removeEntryListener(this);
	}

	@Override
        public void gotEntry(FileObject fileObject, Entry entry) {
		final File file = cvsFileSystem.getLocalFileSystem().getFile(fileObject);
		eventManager.notifyFileInfoListeners(new DefaultFileInfo(fileObject, file));
	}
}

