/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.
 *
 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.command.status;

import org.netbeans.lib.cvsclient.file.FileStatus;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Describes status information for a file. This is the result of doing a
 * cvs status command. The fields in instances of this object are populated
 * by response handlers.
 * @author  Robert Greig
 */
public final class StatusInformation {

	// Fields =================================================================

	private File file;
	private FileStatus status;
	private String workingRevision;
	private String repositoryRevision;
	private String repositoryFileName;
	private String stickyDate;
	private String stickyOptions;
	private String stickyTag;

	/**
	 * Hold key pairs of existing tags.
	 */
	private List tags;

	private StringBuffer symNamesBuffer;

	// Setup ==================================================================

	public StatusInformation() {
		setAllExistingTags(null);
	}

	// Accessing ==============================================================

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public FileStatus getStatus() {
		return status;
	}

	public void setStatus(FileStatus status) {
		this.status = status;
	}

	private String getStatusString() {
		if (status == null) {
			return null;
		}

		return status.toString();
	}

	public void setStatusString(String statusString) {
		setStatus(FileStatus.getStatusForString(statusString));
	}

	public String getWorkingRevision() {
		return workingRevision;
	}

	/**
	 * Setter for property workingRevision.
	 * @param workingRevision New value of property workingRevision.
	 */
	public void setWorkingRevision(String workingRevision) {
		this.workingRevision = workingRevision;
	}

	/**
	 * Getter for property repositoryRevision.
	 * @return Value of property repositoryRevision.
	 */
	public String getRepositoryRevision() {
		return repositoryRevision;
	}

	/**
	 * Setter for property repositoryRevision.
	 * @param repositoryRevision New value of property repositoryRevision.
	 */
	public void setRepositoryRevision(String repositoryRevision) {
		this.repositoryRevision = repositoryRevision;
	}

	public String getRepositoryFileName() {
		return repositoryFileName;
	}

	public void setRepositoryFileName(String repositoryFileName) {
		this.repositoryFileName = repositoryFileName;
	}

	public String getStickyTag() {
		return stickyTag;
	}

	public void setStickyTag(String stickyTag) {
		this.stickyTag = stickyTag;
	}

	public String getStickyDate() {
		return stickyDate;
	}

	public void setStickyDate(String stickyDate) {
		this.stickyDate = stickyDate;
	}

	public String getStickyOptions() {
		return stickyOptions;
	}

	public void setStickyOptions(String stickyOptions) {
		this.stickyOptions = stickyOptions;
	}

	public void addExistingTag(String tagName, String revisionNumber) {
		if (symNamesBuffer == null) {
			symNamesBuffer = new StringBuffer();
		}
		symNamesBuffer.append(tagName);
		symNamesBuffer.append(" ");
		symNamesBuffer.append(revisionNumber);
		symNamesBuffer.append("\n");
	}

	private void createSymNames() {
		tags = new LinkedList();

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
				final SymName newName = new SymName();
				newName.setTag(symName);
				newName.setRevision(revisionNumber);
				tags.add(newName);
				lastLength = length + 1;
				length++;
			}
		}

		symNamesBuffer = null;
	}

	public List getAllExistingTags() {
		if (tags == null) {
			createSymNames();
		}
		return tags;
	}

	private void setAllExistingTags(List tags) {
		this.tags = tags;
	}

	/** Search the symbolic names by number of revision. If not found, return null.
	 */
	public List getSymNamesForRevision(String revNumber) {
		if (tags == null) {
			createSymNames();
		}

		final List list = new LinkedList();

		for (Iterator it = tags.iterator(); it.hasNext();) {
			final StatusInformation.SymName item = (StatusInformation.SymName)it.next();
			if (item.getRevision().equals(revNumber)) {
				list.add(item);
			}
		}
		return list;
	}

	/**
	 * Search the symbolic names by name of tag (symbolic name).
	 * If not found, return null.
	 */
	public StatusInformation.SymName getSymNameForTag(String tagName) {
		if (tags == null) {
			createSymNames();
		}

		for (Iterator it = tags.iterator(); it.hasNext();) {
			final StatusInformation.SymName item = (StatusInformation.SymName)it.next();
			if (item.getTag().equals(tagName)) {
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
		final StringBuffer buf = new StringBuffer();
		buf.append("\nFile: ");
		buf.append((file != null) ? file.getAbsolutePath()
		           : "null");
		buf.append("\nStatus is: ");
		buf.append(getStatusString());
		buf.append("\nWorking revision: ");
		buf.append(workingRevision);
		buf.append("\nRepository revision: ");
		buf.append("\nSticky date: ");
		buf.append(stickyDate);
		buf.append("\nSticky options: ");
		buf.append(stickyOptions);
		buf.append("\nSticky tag: ");
		buf.append(stickyTag);
		if (tags != null && tags.size() > 0) {
			// we are having some tags to print
			buf.append("\nExisting Tags:");
			for (Iterator it = tags.iterator(); it.hasNext();) {
				buf.append("\n  ");
				buf.append(it.next().toString());
			}
		}
		return buf.toString();
	}

	/**
	 * An inner class storing information about a symbolic name.
	 * Consists of a pair of Strings. tag + revision.
	 */
	public static final class SymName {
		private String tag;
		private String revision;

		private SymName() {
		}

		public String getTag() {
			return tag;
		}

		private void setTag(String symName) {
			tag = symName;
		}

		private void setRevision(String rev) {
			revision = rev;
		}

		public String getRevision() {
			return revision;
		}

		public String toString() {
			return getTag() + " : " + getRevision();
		}
	}
}
