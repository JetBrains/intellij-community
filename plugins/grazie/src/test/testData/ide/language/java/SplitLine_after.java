  // Public field within private class can be written/read via reflection even without setAccessible hacks,
  // <caret>so we don't analyze such fields to reduce false-positives
