// "Replace 'if else' with '?:'" "INFORMATION"
class ValueUsedBforeOverwriting {

  void x() {
    String nullable = null;
    String a = nullable;
      a = a == null ? nullable(2) : nullable;
  }

  private String nullable(int i) {
    return null;
  }

}