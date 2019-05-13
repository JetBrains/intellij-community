// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.netbeans.lib.cvsclient.progress.receiving;

import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.ICvsFiles;
import org.netbeans.lib.cvsclient.command.ICvsFilesVisitor;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Thomas Singer
 */
public class AbstractResponseProgressHandler {

	// Fields =================================================================

	private final Set<FileObject> fileObjects = new HashSet<>(2000);
	private final Map<String, FileObjectsCount> directoryPaths = new HashMap<>(200);
	private final IProgressViewer progressViewer;
	private final int maxCount;
	private int count;
	private String previousDirectoryPath;

	// Setup ==================================================================

	protected AbstractResponseProgressHandler(IProgressViewer progressViewer, ICvsFiles cvsFiles) {
		BugLog.getInstance().assertNotNull(progressViewer);
		BugLog.getInstance().assertNotNull(cvsFiles);

		this.progressViewer = progressViewer;

		cvsFiles.visit(new ICvsFilesVisitor() {
			@Override
                        public void handleFile(FileObject fileObject, Entry entry, boolean exists) {
				fileObjects.add(fileObject);
				final DirectoryObject parent = fileObject.getParent();
				assert parent != null;
				addDirectory(parent);
			}

			@Override
                        public void handleDirectory(DirectoryObject directoryObject) {
			}
		});

		maxCount = fileObjects.size();
	}

	// Actions ================================================================

	protected final void fileProcessed(FileObject fileObject) {
		if (!fileObjects.remove(fileObject)) {
			return;
		}

		final FileObjectsCount count = getFileObjectsCount(fileObject.getParentPath());
		if (count == null) {
			return;
		}

		count.dec();

		notifyProgressViewer(1);
	}

	protected final void directoryProcessed(String directoryPath) {
		if (previousDirectoryPath != null && !previousDirectoryPath.equals(directoryPath)) {
			final FileObjectsCount previousDirectoryCount = removeFileObjectsCount(previousDirectoryPath);
			if (previousDirectoryCount != null) {
				notifyProgressViewer(previousDirectoryCount.getUnprocessedFilesInDirectory());
			}
		}

		previousDirectoryPath = directoryPath;
	}

	// Utils ==================================================================

	private FileObjectsCount getFileObjectsCount(String directoryPath) {
		return directoryPaths.get(directoryPath);
	}

	private FileObjectsCount removeFileObjectsCount(String directoryPath) {
		return directoryPaths.remove(directoryPath);
	}

	private void putFileObjectsCount(String directoryPath, FileObjectsCount fileObjectsCount) {
		directoryPaths.put(directoryPath, fileObjectsCount);
	}

	private void addDirectory(DirectoryObject directoryObject) {
		final String directoryPath = directoryObject.getPath();
		FileObjectsCount count = getFileObjectsCount(directoryPath);
		if (count == null) {
			count = new FileObjectsCount();
			putFileObjectsCount(directoryPath, count);
		}

		count.inc();
	}

	private void notifyProgressViewer(int incrementProgress) {
		if (incrementProgress == 0) {
			return;
		}

		count += incrementProgress;
		progressViewer.setProgress((double)count / maxCount);
	}

	// Inner classes ==========================================================

	private static final class FileObjectsCount {

		// Fields =================================================================

		private int fileObjectsPerDirectory;

		// Setup ==================================================================

		FileObjectsCount() {
		}

		// Implemented ============================================================

		public String toString() {
			return String.valueOf(fileObjectsPerDirectory);
		}

		// Accessing ==============================================================

		private void inc() {
			fileObjectsPerDirectory++;
		}

		private void dec() {
			fileObjectsPerDirectory--;
		}

		private int getUnprocessedFilesInDirectory() {
			return fileObjectsPerDirectory;
		}
	}
}
