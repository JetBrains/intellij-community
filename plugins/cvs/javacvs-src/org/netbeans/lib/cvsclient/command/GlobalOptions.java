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
package org.netbeans.lib.cvsclient.command;

import java.util.Map;

/**
 * @author Thomas Singer
 */
public final class GlobalOptions
	implements IGlobalOptions {

	// Fields =================================================================

	private boolean doNoChanges;
	private boolean checkedOutFilesReadOnly;
	private boolean useGzip;
	private boolean noHistoryLogging;
	private boolean someQuiet;
	private Map<String, String> myEnvVariables;

	// Setup ==================================================================

	public GlobalOptions() {
		reset();
	}

	// Implemented ============================================================

	@Override
        public boolean isCheckedOutFilesReadOnly() {
		return checkedOutFilesReadOnly;
	}

	@Override
        public boolean isDoNoChanges() {
		return doNoChanges;
	}

	@Override
        public boolean isNoHistoryLogging() {
		return noHistoryLogging;
	}

	@Override
        public boolean isUseGzip() {
		return useGzip;
	}

	@Override
        public boolean isSomeQuiet() {
		return someQuiet;
	}

	// Accessing ==============================================================

	public void setCheckedOutFilesReadOnly(boolean checkedOutFilesReadOnly) {
		this.checkedOutFilesReadOnly = checkedOutFilesReadOnly;
	}

	public void setDoNoChanges(boolean doNoChanges) {
		this.doNoChanges = doNoChanges;
	}

	public void setNoHistoryLogging(boolean noHistoryLogging) {
		this.noHistoryLogging = noHistoryLogging;
	}

	public void setUseGzip(boolean useGzip) {
		this.useGzip = useGzip;
	}

	public void setSomeQuiet(boolean someQuiet) {
		this.someQuiet = someQuiet;
	}

	@Override
        public Map<String, String> getEnvVariables() {
		return myEnvVariables;
	}

	public void setEnvVariables(Map<String, String> myEnvVariables) {
		this.myEnvVariables = myEnvVariables;
	}
	// Actions ================================================================

	public void reset() {
		setCheckedOutFilesReadOnly(false);
		setDoNoChanges(false);
		setNoHistoryLogging(false);
		setUseGzip(true);
		setSomeQuiet(false);
		setEnvVariables(null);
	}
}
