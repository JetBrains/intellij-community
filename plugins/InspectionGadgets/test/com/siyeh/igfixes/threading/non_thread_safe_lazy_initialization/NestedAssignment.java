public class NestedAssignment {

  private static Object o;

  public static Object getInstance() {
    Object local = null;
    if (o == null) {
      local = o<caret> = new Object();
    }
    return o;
  }
}