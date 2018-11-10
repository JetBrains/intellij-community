// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.netbeans.lib.cvsclient.progress.receiving;

import org.netbeans.lib.cvsclient.command.ICvsFiles;
import org.netbeans.lib.cvsclient.command.IFileInfo;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IDirectoryListener;
import org.netbeans.lib.cvsclient.event.IFileInfoListener;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;

/**
 * @author Thomas Singer
 */
public final class FileInfoAndDirectoryResponseProgressHandler extends AbstractResponseProgressHandler
        implements ICvsListener, IDirectoryListener, IFileInfoListener {

	// Setup ==================================================================

	public FileInfoAndDirectoryResponseProgressHandler(IProgressViewer progressViewer, ICvsFiles cvsFiles) {
		super(progressViewer, cvsFiles);
	}

	// Implemented ============================================================

	@Override
        public void registerListeners(ICvsListenerRegistry listenerRegistry) {
		listenerRegistry.addDirectoryListener(this);
		listenerRegistry.addFileInfoListener(this);
	}

	@Override
        public void unregisterListeners(ICvsListenerRegistry listenerRegistry) {
		listenerRegistry.removeDirectoryListener(this);
		listenerRegistry.removeFileInfoListener(this);
	}

	@Override
        public void processingDirectory(DirectoryObject directoryObject) {
		directoryProcessed(directoryObject.getPath());
	}

	@Override
        public void fileInfoGenerated(Object info) {
		if (info instanceof IFileInfo) {
			final IFileInfo fileInfo = (IFileInfo)info;
			fileProcessed(fileInfo.getFileObject());
		}
	}
}
