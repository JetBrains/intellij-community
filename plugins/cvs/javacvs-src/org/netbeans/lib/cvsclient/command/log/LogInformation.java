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
package org.netbeans.lib.cvsclient.command.log;

import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Describes log information for a file. This is the result of doing a
 * cvs log command. The fields in instances of this object are populated
 * by response handlers.
 * @author  Milos Kleint
 */
public final class LogInformation {

	// Fields =================================================================

	private File file;
	private String rcsFileName;
	private String headRevision;
	private String branch;
	private String accessList;
	private KeywordSubstitution keywordSubstitution;
	private String totalRevisions;
	private String selectedRevisions;
	private String locks;
	private final List<Revision> revisions = new ArrayList<>();
	private List<SymbolicName> symbolicNames;
	private StringBuilder symNamesBuffer;

	// Setup ==================================================================

	public LogInformation() {
	}

	// Accessing ==============================================================

	/** Getter for property file.
	 * @return Value of property file.
	 */
	public File getFile() {
		return file;
	}

	/** Setter for property file.
	 * @param file New value of property file.
	 */
	public void setFile(File file) {
		this.file = file;
	}

	/** Getter for property repositoryFilename.
	 * @return Value of property repositoryFilename.
	 */
	public String getRcsFileName() {
		return rcsFileName;
	}

	/** Setter for property repositoryFilename.
	 * @param rcsFileName New value of property repositoryFilename.
	 */
	public void setRcsFileName(String rcsFileName) {
		this.rcsFileName = rcsFileName.intern();
	}

	/** Getter for property headRevision.
	 * @return Value of property headRevision.
	 */
	public String getHeadRevision() {
		return headRevision;
	}

	/** Setter for property headRevision.
	 * @param headRevision New value of property headRevision.
	 */
	public void setHeadRevision(String headRevision) {
		this.headRevision = headRevision.intern();
	}

	/** Getter for property branch.
	 * @return Value of property branch.
	 */
	public String getBranch() {
		return branch;
	}

	/** Setter for property branch.
	 * @param branch New value of property branch.
	 */
	public void setBranch(String branch) {
		this.branch = branch.intern();
	}

	/** Getter for property accessList.
	 * @return Value of property accessList.
	 */
	public String getAccessList() {
		return accessList;
	}

	/** Setter for property accessList.
	 * @param accessList New value of property accessList.
	 */
	public void setAccessList(String accessList) {
		this.accessList = accessList.intern();
	}

	/** Getter for property keywordSubstitution.
	 * @return Value of property keywordSubstitution.
	 */
	public KeywordSubstitution getKeywordSubstitution() {
		return keywordSubstitution;
	}

	/** Setter for property keywordSubstitution.
	 * @param keywordSubstitution New value of property keywordSubstitution.
	 */
	public void setKeywordSubstitution(KeywordSubstitution keywordSubstitution) {
		this.keywordSubstitution = keywordSubstitution;
	}

	/** Getter for property totalRevisions.
	 * @return Value of property totalRevisions.
	 */
	public String getTotalRevisions() {
		return totalRevisions;
	}

	/** Setter for property totalRevisions.
	 * @param totalRevisions New value of property totalRevisions.
	 */
	public void setTotalRevisions(String totalRevisions) {
		this.totalRevisions = totalRevisions.intern();
	}

	/** Getter for property selectedRevisions.
	 * @return Value of property selectedRevisions.
	 */
	public String getSelectedRevisions() {
		return selectedRevisions;
	}

	/** Setter for property selectedRevisions.
	 * @param selectedRevisions New value of property selectedRevisions.
	 */
	public void setSelectedRevisions(String selectedRevisions) {
		this.selectedRevisions = selectedRevisions.intern();
	}

	/** Getter for property locks.
	 * @return Value of property locks.
	 */
	public String getLocks() {
		return locks;
	}

	/** Setter for property locks.
	 * @param locks New value of property locks.
	 */
	public void setLocks(String locks) {
		this.locks = locks.intern();
	}

	/** adds a revision info  to the LogInformation instance
	 */

	public void addRevision(Revision newRevision) {
		revisions.add(newRevision);
	}

	/** return the all revisions attached to this log
	 * (if more sophisticated method are supplied, this might get obsolete)
	 */
	public List<Revision> getRevisionList() {
		return revisions;
	}

	/** Search the revisions by number of revision. If not found, return null.
	 */
	public Revision getRevision(String number) {
		final Iterator it = revisions.iterator();
		while (it.hasNext()) {
			Revision item = (Revision)it.next();
			if (item.getNumber().equals(number)) {
				return item;
			}
		}
		return null;
	}

	/**
	 * Add a symbolic name to the list of names and attaches it to a revision number.
	 */
	public void addSymbolicName(String symName, String revisionNumber) {
		if (symNamesBuffer == null) {
			symNamesBuffer = new StringBuilder();
		}
		symNamesBuffer.append(symName);
		symNamesBuffer.append(' ');
		symNamesBuffer.append(revisionNumber);
		symNamesBuffer.append('\n');
	}

	private void createSymNames() {
		symbolicNames = new ArrayList<>();
		if (symNamesBuffer == null) {
			return;
		}
		int length = 0;
		int lastLength = 0;
		while (length < symNamesBuffer.length()) {
			while (length < symNamesBuffer.length() && symNamesBuffer.charAt(length) != '\n') {
				length++;
			}
			if (length > lastLength) {
				final String line = symNamesBuffer.substring(lastLength, length);
				final String symName = line.substring(0, line.indexOf(' '));
				final String revisionNumber = line.substring(line.indexOf(' ') + 1);
				final SymbolicName newName = new SymbolicName();
				newName.setName(symName);
				newName.setRevision(revisionNumber);
				symbolicNames.add(newName);
				lastLength = length + 1;
				length++;
			}
		}

		symNamesBuffer = null;
	}

	public List<SymbolicName> getAllSymbolicNames() {
		if (symbolicNames == null) {
			createSymNames();
		}
		return symbolicNames;
	}

	/** Search the symbolic names by number of revision. If not found, return null.
	 */
	public List<SymbolicName> getSymNamesForRevision(String revNumber) {
		if (symbolicNames == null) {
			createSymNames();
		}
		final List<SymbolicName> list = new ArrayList<>();
		for (int i = 0; i < symbolicNames.size(); i++) {
			final SymbolicName symbolicName = symbolicNames.get(i);
			if (symbolicName.getRevision().equals(revNumber)) {
				list.add(symbolicName);
			}
		}
		return list;
	}

	/** Search the symbolic names by name of tag (symbolic name). If not found, return null.
	 */
	public SymbolicName getSymName(String symName) {
		if (symbolicNames == null) {
			createSymNames();
		}
		for (Iterator it = symbolicNames.iterator(); it.hasNext();) {
			final SymbolicName item = (SymbolicName)it.next();
			if (item.getName().equals(symName)) {
				return item;
			}
		}
		return null;
	}

	/**
	 * Return a string representation of this object. Useful for debugging.
	 */
	@SuppressWarnings({"HardCodedStringLiteral"})
	public String toString() {
		final String absolutePath = (file != null) ? file.getAbsolutePath() : "null";
		return "File: " + absolutePath + "\nRepositoryFile: " + rcsFileName + "\nHead revision: " + headRevision;
	}

}
