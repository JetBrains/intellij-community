  // Public field within private class can be written/read via reflection even without setAccessible hacks
  // <caret><warning descr="COMMA_COMPOUND_SENTENCE">so</warning> we don't analyze such fields to reduce false-positives
