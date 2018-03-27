import java.util.Objects;

class T {
  static boolean same(String t, String s) {
      //c1
      return Objects.equals(s, t + "a");
  }
}