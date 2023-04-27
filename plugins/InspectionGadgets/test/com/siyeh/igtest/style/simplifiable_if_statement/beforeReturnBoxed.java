// "Replace 'if else' with '?:'" "true"

class Test {
    private Double max(final Double a, final Double b) {
      <caret>if (a != null && b != null) {
        return Math.max(a, b);
      } else {
        return a != null ? a : b;
      }
    }
}