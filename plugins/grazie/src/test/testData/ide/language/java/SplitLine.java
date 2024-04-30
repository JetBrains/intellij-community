  // Public field within private class can be written/read via reflection even without setAccessible hacks
  // <caret><GRAMMAR_ERROR descr="COMMA_COMPOUND_SENTENCE">so</GRAMMAR_ERROR> we don't analyze such fields to reduce false-positives
