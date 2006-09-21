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
package org.netbeans.lib.cvsclient.command.reservedcheckout;

import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.AbstractMessageParser;
import org.netbeans.lib.cvsclient.command.ICvsFiles;
import org.netbeans.lib.cvsclient.command.ICvsFilesVisitor;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.file.FileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author  Thomas Singer
 */
final class EditorsMessageParser extends AbstractMessageParser {

	// Constants ==============================================================

  @NonNls private static final String DATE_FORMAT_STR = "MMM dd hh:mm:ss yyyy";
  private static final SyncDateFormat DATE_FORMAT = new SyncDateFormat(new SimpleDateFormat(DATE_FORMAT_STR, Locale.US));

	// Fields =================================================================

	private final IEventSender eventManager;
	private final ICvsFileSystem cvsFileSystem;
	private final Set fileSet = new HashSet();

	private transient EditorsFileInfoContainer editorsFileInfo;

	// Setup ==================================================================

	public EditorsMessageParser(IEventSender eventManager, final ICvsFileSystem cvsFileSystem, ICvsFiles cvsFiles) {
		this.eventManager = eventManager;
		this.cvsFileSystem = cvsFileSystem;

		cvsFiles.visit(new ICvsFilesVisitor() {
			public void handleFile(FileObject fileObject, Entry entry, boolean exists) {
				final File file = cvsFileSystem.getLocalFileSystem().getFile(fileObject);
				fileSet.add(file);
			}

			public void handleDirectory(DirectoryObject directoryObject) {
			}
		});
	}

	// Implemented ============================================================

	public void parseLine(String line, boolean isErrorMessage) {
		if (isErrorMessage) {
			return;
		}

		final TabStringTokenizer tokenizer = new TabStringTokenizer(line);
		final String fileName = tokenizer.nextToken();
		final String user = tokenizer.nextToken();
		final String dateString = tokenizer.nextToken();
		final String clientName = tokenizer.nextToken();
		final String editDirectory = tokenizer.nextToken();
		if (editDirectory == null) {
			return;
		}

		if (fileName.length() > 0) {
			final File file = cvsFileSystem.getLocalFileSystem().getFile(fileName);
			if (editorsFileInfo != null && !editorsFileInfo.getFile().equals(file)) {
				fireFileInfoEvent(editorsFileInfo, true);
				editorsFileInfo = null;
			}
			if (editorsFileInfo == null) {
				editorsFileInfo = new EditorsFileInfoContainer(file);
			}
		}
		else {
			if (editorsFileInfo == null) {
				return;
			}
		}

		try {
			final Date date = parseDate(dateString);

			editorsFileInfo.addEditor(date, user, clientName, editDirectory);
		}
		catch (ParseException ex) {
			return;
		}
	}

	public void outputDone() {
		if (editorsFileInfo != null) {
			fireFileInfoEvent(editorsFileInfo, true);
			editorsFileInfo = null;
		}

		for (Iterator it = fileSet.iterator(); it.hasNext();) {
			final File file = (File)it.next();
			fireFileInfoEvent(new EditorsFileInfoContainer(file), false);
			it.remove();
		}
	}

	// Utils ==================================================================

	private void fireFileInfoEvent(EditorsFileInfoContainer editorsFileInfo, boolean remove) {
		eventManager.notifyFileInfoListeners(editorsFileInfo);

		if (remove) {
			fileSet.remove(editorsFileInfo.getFile());
		}
	}

	private static Date parseDate(String dateString) throws ParseException {
		// strip day of week
		final int firstSpaceIndex = Math.max(dateString.indexOf(' '), 0);
		// strip time zone
		final int lastSpaceIndex = Math.min(dateString.lastIndexOf(' '), dateString.length());

		dateString = dateString.substring(firstSpaceIndex, lastSpaceIndex).trim();

		return DATE_FORMAT.parse(dateString);
	}

        public void binaryMessageSent(final byte[] bytes) {
        }
}
