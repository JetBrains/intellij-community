public class LocalVariableReferenced {

  private static Object o;

  public static Object getInstance(int i) {
    if (o == null) {
      o<caret> = "" + i;
    }
    return o;
  }
}