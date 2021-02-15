public class BooleanExpression {
  boolean or(Double v1, Double v2) {
    return (v1 == null || v2 == null) && v1 == v2;
  }

  boolean nand(Double v1, Double v2) {
    return !(v2 != null && v1 != null) && v1 == v2;
  }

  Number n1, n2;

  boolean differentQualifiers(BooleanExpression other) {
    return  (other.n1 == null || other.n2 == null) && n1 <warning descr="Number objects are compared using '==', not 'equals()'">==</warning> n2;
  }
}
