public class Test {
  <T extends String> void foo (T t1, T t2) {
        new A().<caret>bar (t1, t2);
  }
}