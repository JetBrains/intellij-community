package org.netbeans.lib.cvsclient.admin;

import java.io.File;

/**
 * author: lesya
 */
public class InvalidEntryFormatException extends RuntimeException{
   private File entriesFile;

	public void setEntriesFile(File directory) {
		this.entriesFile = directory;
	}

	public File getEntriesFile() {
		return entriesFile;
	}
}
