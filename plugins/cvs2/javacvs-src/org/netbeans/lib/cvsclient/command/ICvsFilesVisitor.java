package org.netbeans.lib.cvsclient.command;

import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;

/**
 * @author Thomas Singer
 */
public interface ICvsFilesVisitor {

	void handleFile(FileObject fileObject, Entry entry, boolean exists);

	void handleDirectory(DirectoryObject directoryObject);
}
