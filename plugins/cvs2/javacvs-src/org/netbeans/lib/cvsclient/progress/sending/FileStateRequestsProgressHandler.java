package org.netbeans.lib.cvsclient.progress.sending;

import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.ICvsFiles;
import org.netbeans.lib.cvsclient.command.ICvsFilesVisitor;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.RangeProgressViewer;
import org.netbeans.lib.cvsclient.request.AbstractFileStateRequest;
import org.netbeans.lib.cvsclient.request.IRequest;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Thomas Singer
 */
public class FileStateRequestsProgressHandler
        implements IRequestsProgressHandler {

	// TODO: inline me
	public static IRequestsProgressHandler create(IProgressViewer parentProgressViewer, ICvsFiles cvsFiles) {
		return new FileStateRequestsProgressHandler(new RangeProgressViewer(parentProgressViewer, 0.0, 0.5), cvsFiles);
	}

	// Fields =================================================================

	private final Set fileObjects = new HashSet(2000);
	private final IProgressViewer progressViewer;
	private int count;
	private final int maxCount;

	// Setup ==================================================================

	public FileStateRequestsProgressHandler(IProgressViewer progressViewer, ICvsFiles cvsFiles) {
		BugLog.getInstance().assertNotNull(progressViewer);
		BugLog.getInstance().assertNotNull(cvsFiles);

		this.progressViewer = progressViewer;

		cvsFiles.visit(new ICvsFilesVisitor() {
			public void handleFile(FileObject fileObject, Entry entry, boolean exists) {
                fileObjects.add(fileObject);
			}

			public void handleDirectory(DirectoryObject directoryObject) {
			}
		});

		count = 0;
		maxCount = fileObjects.size();
	}

	public void requestSent(IRequest request) {
		if (!(request instanceof AbstractFileStateRequest)) {
			return;
		}

		final AbstractFileStateRequest fileStateRequest = (AbstractFileStateRequest)request;
		final FileObject fileObject = fileStateRequest.getFileObject();
		if (!fileObjects.remove(fileObject)) {
			return;
		}

		count++;
		notifyProgress(count, maxCount, progressViewer);
	}

	// Utils ==================================================================

	private void notifyProgress(int count, int maxCount, IProgressViewer progressViewer) {
		progressViewer.setProgress((double)count / maxCount);
	}
}
