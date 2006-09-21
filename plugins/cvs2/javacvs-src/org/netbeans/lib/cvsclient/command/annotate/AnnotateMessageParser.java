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
package org.netbeans.lib.cvsclient.command.annotate;

import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.command.AbstractMessageParser;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles the building of a annotate information object and the firing of
 * events when complete objects are built.
 *
 * @author  Milos Kleint
 */
final class AnnotateMessageParser extends AbstractMessageParser {

	// Constants ==============================================================

	@NonNls private static final String ANNOTATING = "Annotations for ";
	private static final String SEPARATOR1 = " (";
	private static final String SEPARATOR2 = "): ";
        @NonNls private static final String ANNOTATIONS_DATE_FORMAT_STR = "dd-MMM-yy";
        private static final SyncDateFormat DATE_FORMAT = new SyncDateFormat(new SimpleDateFormat(ANNOTATIONS_DATE_FORMAT_STR, Locale.US));


	// Fields =================================================================

	private final IEventSender eventManager;
	private final ICvsFileSystem cvsFileSystem;

	private AnnotateInformation annotateInformation;

	// Setup ==================================================================

	public AnnotateMessageParser(IEventSender eventManager, ICvsFileSystem cvsFileSystem) {
		this.eventManager = eventManager;
		this.cvsFileSystem = cvsFileSystem;
	}

	// Implemented ============================================================

	protected void outputDone() {
		if (annotateInformation == null) {
			return;
		}

		eventManager.notifyFileInfoListeners(annotateInformation);
		annotateInformation = null;
	}

	public void parseLine(String line, boolean isErrorMessage) {
		if (isErrorMessage) {
			if (line.startsWith(ANNOTATING)) {
				outputDone();

				final String relativeFileName = line.substring(ANNOTATING.length());
				final File file = cvsFileSystem.getLocalFileSystem().getFile(relativeFileName);
				annotateInformation = new AnnotateInformation(file);
			}
		}
		else {
			processLine(line);
		}
	}

	// Utils ==================================================================

	private void processLine(String line) {
		if (annotateInformation == null) {
			return;
		}

		final int indexOpeningBracket = line.indexOf(SEPARATOR1);
		if (indexOpeningBracket < 0) {
			return;
		}

		final int indexClosingBracket = line.indexOf(SEPARATOR2, indexOpeningBracket + 1);
		if (indexClosingBracket < 0) {
			return;
		}

		final String revision = line.substring(0, indexOpeningBracket).trim();
		final String authorAndDate = line.substring(indexOpeningBracket + SEPARATOR1.length(), indexClosingBracket);
		final String contents = line.substring(indexClosingBracket + SEPARATOR2.length());
		final int lastSpace = authorAndDate.lastIndexOf(' ');
		if (lastSpace < 0) {
			return;
		}

		final String author = authorAndDate.substring(0, lastSpace).trim();
		final String dateString = authorAndDate.substring(lastSpace + 1);

		final Date date;
		try {
			date = DATE_FORMAT.parse(dateString);
		}
		catch (ParseException ex) {
			BugLog.getInstance().showException(ex);
			return;
		}

		annotateInformation.addLine(new AnnotateLine(revision, author, date, contents));
	}

        public void binaryMessageSent(final byte[] bytes) {
        }
}
