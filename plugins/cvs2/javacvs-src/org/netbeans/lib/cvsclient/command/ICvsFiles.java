package org.netbeans.lib.cvsclient.command;

/**
 * @author Thomas Singer
 */
public interface ICvsFiles {

	void visit(ICvsFilesVisitor visitor);
}
