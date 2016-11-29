public class EnumComparison {

  enum E { A, B }

  boolean a(E a, E b) {
    return a ==<caret> b;
  }
}