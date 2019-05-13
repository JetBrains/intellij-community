// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.netbeans.lib.cvsclient.progress.receiving;

import org.netbeans.lib.cvsclient.command.CommandUtils;
import org.netbeans.lib.cvsclient.command.ICvsFiles;
import org.netbeans.lib.cvsclient.command.IFileInfo;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IFileInfoListener;
import org.netbeans.lib.cvsclient.event.IMessageListener;
import org.netbeans.lib.cvsclient.file.FileUtils;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.util.BugLog;

/**
 * @author Thomas Singer
 */
public final class FileInfoAndMessageResponseProgressHandler extends AbstractResponseProgressHandler
        implements ICvsListener, IMessageListener, IFileInfoListener {

	// Fields =================================================================

	private final String examinePattern;

	// Setup ==================================================================

	public FileInfoAndMessageResponseProgressHandler(IProgressViewer progressViewer, ICvsFiles cvsFiles, String examinePattern) {
		super(progressViewer, cvsFiles);

		BugLog.getInstance().assertNotNull(examinePattern);

		this.examinePattern = examinePattern;
	}

	// Implemented ============================================================

	@Override
        public void registerListeners(ICvsListenerRegistry listenerRegistry) {
		listenerRegistry.addMessageListener(this);
		listenerRegistry.addFileInfoListener(this);
	}

	@Override
        public void unregisterListeners(ICvsListenerRegistry listenerRegistry) {
		listenerRegistry.removeMessageListener(this);
		listenerRegistry.removeFileInfoListener(this);
	}

	@Override
        public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
		if (!error || tagged) {
			return;
		}

		final String directoryPath = CommandUtils.getExaminedDirectory(message, examinePattern);
		if (directoryPath == null) {
			return;
		}

		if (directoryPath.equals(".")) {
			directoryProcessed("/");
		}
		else {
			directoryProcessed(FileUtils.ensureLeadingSlash(directoryPath));
		}
	}

	@Override
        public void fileInfoGenerated(Object info) {
		if (info instanceof IFileInfo) {
			final IFileInfo fileInfo = (IFileInfo)info;
			fileProcessed(fileInfo.getFileObject());
		}
	}

        @Override
        public void binaryMessageSent(final byte[] bytes) {
        }
}
