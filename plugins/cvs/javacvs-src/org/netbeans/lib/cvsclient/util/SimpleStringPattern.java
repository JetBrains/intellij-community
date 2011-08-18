/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Thomas Singer.
 * Portions created by Thomas Singer Copyright (C) 2001.
 * All Rights Reserved.
 *
 * Contributor(s): Thomas Singer, Milos Kleint
 *****************************************************************************/
package org.netbeans.lib.cvsclient.util;

import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * @author  Thomas Singer
 */
public final class SimpleStringPattern
        implements IStringPattern {

	private static final char MATCH_EACH = '*';
	private static final char MATCH_ONE = '?';

	private final List<SubPattern> subPatterns = new ArrayList();

	/**
	 * Creates a SimpleStringPattern for the specified definition.
	 * The definition might contain two special characters ('*' and '?').
	 */
	public SimpleStringPattern(@NonNls String definition) {
		splitInSubPattern(definition);
	}

	/**
	 * Returns whether the specified string matches thiz pattern.
	 */
	public boolean doesMatch(String string) {
		int index = 0;
		SubPattern subPattern = null;
		for (int i = 0, length = subPatterns.size(); i < length ; i++) {
			subPattern = subPatterns.get(i);
			index = subPattern.doesMatch(string, index);
			if (index < 0) {
				return false;
			}
		}

		if (index == string.length()) {
			return true;
		}
		if (subPattern == null) {
			return false;
		}
		return subPattern.checkEnding(string);
	}

	private void splitInSubPattern(String definition) {
		char prevSubPattern = ' ';

		int prevIndex = 0;
		for (int index = 0; index >= 0;) {
			prevIndex = index;

			index = definition.indexOf(MATCH_EACH, prevIndex);
			if (index >= 0) {
				final String match = definition.substring(prevIndex, index);
				addSubPattern(match, prevSubPattern);
				prevSubPattern = MATCH_EACH;
				index++;
				continue;
			}
			index = definition.indexOf(MATCH_ONE, prevIndex);
			if (index >= 0) {
				final String match = definition.substring(prevIndex, index);
				addSubPattern(match, prevSubPattern);
				prevSubPattern = MATCH_ONE;
				index++;
				continue;
			}
		}
		final String match = definition.substring(prevIndex);
		addSubPattern(match, prevSubPattern);
	}

	private void addSubPattern(String match, char subPatternMode) {
		final SubPattern subPattern;
		switch (subPatternMode) {
		case MATCH_EACH:
			subPattern = new MatchEachCharPattern(match);
			break;
		case MATCH_ONE:
			subPattern = new MatchOneCharPattern(match);
			break;
		default:
			subPattern = new MatchExactSubPattern(match);
			break;
		}

		subPatterns.add(subPattern);
	}

	public String toString() {
          final StringBuilder buffer = new StringBuilder();
		for (int i = 0, length = subPatterns.size(); i < length; i++) {
			final SubPattern subPattern = subPatterns.get(i);
			buffer.append(subPattern.toString());
		}
		return buffer.toString();
	}

	private static abstract class SubPattern {
		protected final String match;

		protected SubPattern(String match) {
			this.match = match;
		}

		/**
		 * @param string ... the whole string to compile for matching
		 * @param index  ... the index in string where this' compile should begin
		 * @return       ... if successful the next compile-position, if not -1
		 */
		public abstract int doesMatch(String string, int index);

		public boolean checkEnding(String string) {
			return false;
		}
	}

	private static class MatchExactSubPattern extends SubPattern {
		public MatchExactSubPattern(String match) {
			super(match);
		}

		public int doesMatch(String string, int index) {
			if (!string.startsWith(match, index)) {
				return -1;
			}
			return index + match.length();
		}

		public String toString() {
			return match;
		}
	}

	private static final class MatchEachCharPattern extends SubPattern {
		private MatchEachCharPattern(String match) {
			super(match);
		}

		public int doesMatch(String string, int index) {
			final int matchIndex = string.indexOf(match, index);
			if (matchIndex < 0) {
				return -1;
			}
			return matchIndex + match.length();
		}

		public boolean checkEnding(String string) {
			return string.endsWith(match);
		}

		public String toString() {
			return MATCH_EACH + match;
		}
	}

	private static final class MatchOneCharPattern extends MatchExactSubPattern {
		private MatchOneCharPattern(String match) {
			super(match);
		}

		public int doesMatch(String string, int index) {
			index++;
			if (string.length() < index) {
				return -1;
			}
			return super.doesMatch(string, index);
		}

		public String toString() {
			return MATCH_ONE + match;
		}
	}
}
