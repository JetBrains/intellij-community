public class NegatedObjectOldSafeComparison {
  boolean a(Object a, Object b) {
    return a == null ? b != null : !a.equals(b);
  }
}