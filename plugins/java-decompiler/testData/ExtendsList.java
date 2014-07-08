public class ExtendsList {
  static <T extends Comparable<? super T>> T m1(T t) {
    return null;
  }

  static <T extends Object & Comparable<? super T>> T m2(T t) {
    return null;
  }
}