import java.util.Comparator;

class A {
  int get() {
    return 1;
  }

  Comparator<A> comparator = Comparator.comparingInt(A::ge<caret>t);
}