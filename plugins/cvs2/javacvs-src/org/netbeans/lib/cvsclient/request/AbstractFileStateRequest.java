package org.netbeans.lib.cvsclient.request;

import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.util.BugLog;

/**
 * @author Thomas Singer
 */
public abstract class AbstractFileStateRequest extends AbstractRequest {

	// Fields =================================================================

	private final FileObject fileObject;

	// Setup ==================================================================

	protected AbstractFileStateRequest(FileObject fileObject) {
		BugLog.getInstance().assertNotNull(fileObject);

		this.fileObject = fileObject;
	}

	// Accessing ==============================================================

	protected final String getFileName() {
		return fileObject.getName();
	}

	public final FileObject getFileObject() {
		return fileObject;
	}
}
