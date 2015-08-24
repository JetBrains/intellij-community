class Precedence {

  public static boolean original(boolean a, boolean b, boolean c, boolean d) {

    <caret>if (!(a || b)) {
      return false;
    }

    return c || d;
  }
}