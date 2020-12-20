public class Ternary {
  boolean then(Double v1, Double v2) {
    return v1 == null || v2 == null ? v1 == v2 : Math.random() > 0.5;
  }

  boolean elze(Double v1, Double v2) {
    return v1 != null && v2 != null ? Math.random() > 0.5 : v2 == v1;
  }

  boolean isNegatedThen(Double v1, Double v2) {
    return !(v2 != null && v1 != null) ? v1 == v2 : Math.random() > 0.5;
  }

  boolean isNegatedElse(Double v1, Double v2) {
    return !(v2 == null || v1 == null) ? Math.random() > 0.5 : v2 == v1;
  }

  boolean withParentheses(Double v1, Double v2) {
    return ((((v1 == null)) || ((v2 == null)))) ? ((((v1)) == ((v2)))) : Math.random() > 0.5;
  }

  boolean numberEqualityIsPartOfBiggerExpression(Double v1, Double v2) {
    return v1 == null || v2 == null ? Math.random() > 0.3 || (v1 == v2 && Math.random() > 0.7) : Math.random() > 0.5;
  }

  Number n1, n2;

  boolean differentQualifiers(Ternary other) {
    return  (other.n1 == null || other.n2 == null) ? n1 <warning descr="Number objects are compared using '==', not 'equals()'">==</warning> n2 : false;
  }
}