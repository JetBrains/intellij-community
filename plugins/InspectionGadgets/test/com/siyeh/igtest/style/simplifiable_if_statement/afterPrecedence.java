// "Replace 'if else' with '&&'" "true"
class Precedence {

  public static boolean original(boolean a, boolean b, boolean c, boolean d) {

      return (a || b) && (c || d);

  }
}