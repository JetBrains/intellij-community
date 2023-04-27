// "Replace 'if else' with '?:'" "true"

class Test {
    private Double foo(Double b) {
      <caret>if (b != null) {
        return 0D;
      } else {
        return null;
      }
    }
}