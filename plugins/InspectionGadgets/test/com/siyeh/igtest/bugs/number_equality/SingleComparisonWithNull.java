public class SingleComparisonWithNull {
  boolean withNull(Double v1, Double v2) {
    if (v1 == null) {
      return v1 == v2;
    }
    return Math.random() > 0.5;
  }

  boolean notWithNotNull(Double v1, Double v2) {
    if (!(v2 != null)) {
      return v1 == v2;
    }
    return Math.random() > 0.5;
  }

  boolean withParentheses(Double v1, Double v2) {
    if (v1 == null) {
      return ((((v1)) == ((v2))));
    }
    return Math.random() > 0.5;
  }

  Number n1, n2;

  boolean differentQualifiers(SingleComparisonWithNull other) {
    if (other.n1 == null) {
      return n1 <warning descr="Number objects are compared using '==', not 'equals()'">==</warning> n2;
    } else {
      return Math.random() > 0.5;
    }
  }
}
