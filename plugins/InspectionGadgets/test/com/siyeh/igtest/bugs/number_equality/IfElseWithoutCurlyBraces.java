public class IfElseWithoutCurlyBraces {
  boolean then(Double v1, Double v2) {
    if (v1 == null || v2 == null) return v1 == v2;
    else return Math.random() > 0.5;
  }

  boolean elze(Double v1, Double v2) {
    if (v1 != null && v2 != null) return Math.random() > 0.5;
    else return v2 == v1;
  }

  boolean isNegatedThen(Double v1, Double v2) {
    if (!(v2 != null && v1 != null)) return v1 == v2;
    else return Math.random() > 0.5;
  }

  boolean isNegatedElse(Double v1, Double v2) {
    if (!(v2 == null || v1 == null)) return Math.random() > 0.5;
    else return v2 == v1;
  }

  boolean withParentheses(Double v1, Double v2) {
    if ((((v1 == null)) || ((v2 == null)))) return ((((v1)) == ((v2))));
    else return Math.random() > 0.5;
  }

  boolean numberEqualityIsPartOfBiggerExpression(Double v1, Double v2) {
    if (v1 == null || v2 == null)
      return Math.random() > 0.3 || (v1 == v2 && Math.random() > 0.7);
    else
      return Math.random() > 0.5;
  }

  void methodCall(Double v1, Double v2) {
    if (v1 == null || v2 == null)
      System.out.println(v1 == v2);
    else
      System.out.println(Math.random() > 0.5);
  }

  boolean assignment(Double v1, Double v2) {
    final boolean b;
    if (v1 == null || v2 == null)
      b = Math.random() > 0.3 || (v2 == v1 && Math.random() > 0.7);
    else
      b = Math.random() > 0.5;
    return b;
  }

  boolean outerIf(Double v1, Double v2) {
    if (v1 == null || v2 == null)
      if (Math.random() > 0.2)
        return Math.random() > 0.1 ? v1 == v2 : Math.random() < 0.3;
    return Math.random() > 0.5;
  }

  Number n1, n2;

  boolean differentQualifiers(IfElseWithoutCurlyBraces other) {
    if (other.n1 == null || other.n2 == null) return n1 <warning descr="Number objects are compared using '==', not 'equals()'">==</warning> n2;
    else return Math.random() > 0.5;
  }
}
