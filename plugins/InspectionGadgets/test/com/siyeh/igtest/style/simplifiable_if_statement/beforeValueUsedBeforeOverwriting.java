// "Replace 'if else' with '?:'" "INFORMATION"
class ValueUsedBforeOverwriting {

  void x() {
    String nullable = null;
    String a = nullable;
    if<caret> (a == null) {
      a = nullable(2);
    }
  }

  private String nullable(int i) {
    return null;
  }

}