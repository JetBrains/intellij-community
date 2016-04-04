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
package org.netbeans.lib.cvsclient.command.status;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.command.AbstractMessageParser;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.FileStatus;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Handles the building of a status information object and the firing of
 * events when complete objects are built.
 *
 * @author  Milos Kleint
 * @author  Thomas Singer
 */
final class StatusMessageParser extends AbstractMessageParser {

	// Constants ==============================================================

	@NonNls private static final String NO_REVISION_CONTROL_FILE = "No revision control file";
	private static final String SEPARATOR = "===================================================================";

	@NonNls private static final String FILE = "File: ";
	@NonNls private static final String STATUS = "\tStatus: ";
	@NonNls private static final String NO_FILE = "no file ";
	@NonNls private static final String WORK_REVISION = "   Working revision:\t";
	@NonNls private static final String REPOSITORY_REVISION = "   Repository revision:\t";
	@NonNls private static final String STICKY_TAG = "   Sticky Tag:";
	@NonNls private static final String STICKY_DATE = "   Sticky Date:";
	@NonNls private static final String STICKY_OPTIONS = "   Sticky Options:";
	@NonNls private static final String EXISTING_TAGS = "   Existing Tags:";
	@NonNls private static final String NO_TAGS = "   No Tags Exist";
	@NonNls private static final String NO_ENTRY_FOR = "No entry for ";
	private static final String QUESTION_MARK = "? ";

	// Fields =================================================================

	private final List fileObjects = new ArrayList();
	private final ICvsFileSystem cvsFileSystem;
	private final IEventSender eventSender;

	private StatusInformation statusInformation;
	private String relativeDirectory;
	private boolean beginning;
	private boolean readingTags;

	// Setup ==================================================================

	public StatusMessageParser(IEventSender eventSender, List fileObjects, ICvsFileSystem cvsFileSystem) {
		BugLog.getInstance().assertNotNull(eventSender);

		this.eventSender = eventSender;
		this.fileObjects.addAll(fileObjects);

		this.cvsFileSystem = cvsFileSystem;

		this.beginning = true;
	}

	// Implemented ============================================================

	protected void outputDone() {
		if (statusInformation != null) {
			eventSender.notifyFileInfoListeners(statusInformation);
			statusInformation = null;
			beginning = true;
			readingTags = false;
		}
	}

	public void parseLine(String line, boolean isErrorMessage) {
		if (readingTags) {
			if (line.startsWith(NO_TAGS)) {
				outputDone();
				return;
			}

			final int bracket = line.indexOf('(');
			if (bracket > 0) {
				// it's another tag..
				final String tag = line.substring(0, bracket - 1).trim();
				final String rev = line.substring(bracket + 1, line.length() - 1);

				if (statusInformation == null) {
					statusInformation = new StatusInformation();
				}
				statusInformation.addExistingTag(tag, rev);
			}
			else {
				outputDone();
				return;
			}
		}

		if (isErrorMessage) {
			final int index = line.indexOf(StatusCommand.EXAM_DIR);
			if (index >= 0) {
				relativeDirectory = line.substring(index + StatusCommand.EXAM_DIR.length()).trim();
				beginning = true;
			}
			return;
		}

		if (beginning) {
			if (line.startsWith(QUESTION_MARK)) {
				final File file = cvsFileSystem.getLocalFileSystem().getFile(line.substring(QUESTION_MARK.length()));
				statusInformation = new StatusInformation();
				statusInformation.setFile(file);
				statusInformation.setStatus(FileStatus.UNKNOWN);
				outputDone();
			}

			if (line.startsWith(FILE)) {
				final int statusIndex = line.lastIndexOf(STATUS);
				final String statusString = line.substring(statusIndex + STATUS.length());
				final FileStatus status = FileStatus.getStatusForString(statusString);
				String fileName = line.substring(FILE.length(), statusIndex).trim();
				fileName = StringUtil.trimStart(fileName, NO_FILE);

				outputDone();

				statusInformation = new StatusInformation();
				statusInformation.setFile(createFile(fileName));
				statusInformation.setStatus(status);
				beginning = false;
				return;
			}

//			int index = line.indexOf(NOTHING_KNOWN_ABOUT);
//			if (index >= 0) {
//				final String fileName = line.substring(index + NOTHING_KNOWN_ABOUT.length());
//
//				createStatusInformation(fileName, true, FileStatus.UNKNOWN.toString());
//				return;
//			}
		}
		else {
			if (line.startsWith(WORK_REVISION)) {
				String workingRevision = line.substring(WORK_REVISION.length());
				if (workingRevision.startsWith(NO_ENTRY_FOR)) {
					workingRevision = "";
				}
				statusInformation.setWorkingRevision(workingRevision);
			}
			else if (line.startsWith(REPOSITORY_REVISION)) {
				final String repositoryRevision = line.substring(REPOSITORY_REVISION.length());
				statusInformation.setRepositoryRevision("");
				statusInformation.setRepositoryFileName("");
				if (!repositoryRevision.equals(NO_REVISION_CONTROL_FILE)) {
					final int separatorIndex = repositoryRevision.indexOf('\t');
					if (separatorIndex > 0) {
						statusInformation.setRepositoryRevision(repositoryRevision.substring(0, separatorIndex).trim());
						statusInformation.setRepositoryFileName(repositoryRevision.substring(separatorIndex).trim());
					}
				}
			}
			else if (line.startsWith(STICKY_TAG)) {
				final String stickyTag = line.substring(STICKY_TAG.length()).trim();
				statusInformation.setStickyTag(stickyTag);
			}
			else if (line.startsWith(STICKY_DATE)) {
				final String stickyDate = line.substring(STICKY_DATE.length()).trim();
				statusInformation.setStickyDate(stickyDate);
			}
			else if (line.startsWith(STICKY_OPTIONS)) {
				final String stickyOptions = line.substring(STICKY_OPTIONS.length()).trim();
				statusInformation.setStickyOptions(stickyOptions);
			}
			else if (line.startsWith(EXISTING_TAGS)) {
				readingTags = true;
			}
			else if (line.equals(SEPARATOR)) {
				outputDone();
			}
		}
	}

	// Utils ==================================================================

	private File createFile(String fileName) {
		File file = null;

		if (relativeDirectory != null) {
			if (relativeDirectory.trim().equals(".")) {
				file = cvsFileSystem.getLocalFileSystem().getFile(fileName);
			}
			else {
				file = cvsFileSystem.getLocalFileSystem().getFile(relativeDirectory + '/' + fileName);
			}
		}
		else {
			for (Iterator it = fileObjects.iterator(); it.hasNext();) {
				final AbstractFileObject abstractFileObject = (AbstractFileObject)it.next();
				if (abstractFileObject instanceof FileObject) {
					final FileObject fileObject = (FileObject)abstractFileObject;

					if (!fileObject.getName().equals(fileName)) {
						continue;
					}

					it.remove();

					return cvsFileSystem.getLocalFileSystem().getFile(fileObject);
				}
				it.remove();
			}
		}
		BugLog.getInstance().assertTrue(file != null, "Wrong algorithm for detecting file name (" + fileName + ")");
		return file;
	}

        public void binaryMessageSent(final byte[] bytes) {
        }
}
