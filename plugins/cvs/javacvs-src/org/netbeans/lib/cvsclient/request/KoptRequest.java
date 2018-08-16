package org.netbeans.lib.cvsclient.request;

import org.netbeans.lib.cvsclient.command.KeywordSubstitution;
import org.netbeans.lib.cvsclient.util.BugLog;

/**
 * @author  Thomas Singer
 */
public class KoptRequest extends AbstractRequest {

	// Fields =================================================================

	private final KeywordSubstitution keywordSubstMode;

	// Setup ==================================================================

	public KoptRequest(KeywordSubstitution keywordSubstMode) {
		BugLog.getInstance().assertNotNull(keywordSubstMode);

		this.keywordSubstMode = keywordSubstMode;
	}

	// Implemented ============================================================

	@Override
        public String getRequestString() {
		return "Kopt -k" + keywordSubstMode.toString() + '\n';
	}
}
