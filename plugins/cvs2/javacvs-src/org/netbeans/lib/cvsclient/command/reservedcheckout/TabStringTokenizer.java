package org.netbeans.lib.cvsclient.command.reservedcheckout;

/**
 * @author Thomas Singer
 */
public final class TabStringTokenizer {

	// Fields =================================================================

	private final String line;
	private int startIndex;
	private String nextToken;

	// Setup ==================================================================

	public TabStringTokenizer(String line) {
		this.line = line;

		fetchNextToken();
	}

	// Actions ================================================================

	public String nextToken() {
		final String token = nextToken;
		fetchNextToken();
		return token;
	}

	// Utils ==================================================================

	private void fetchNextToken() {
		nextToken = null;
		if (startIndex >= line.length()) {
			return;
		}

		final int nextIndex = line.indexOf('\t', startIndex);
		if (nextIndex >= 0) {
			nextToken = line.substring(startIndex, nextIndex);
			startIndex = nextIndex + 1;
		}
		else {
			nextToken = line.substring(startIndex);
			startIndex = line.length();
		}
	}
}
