public class LocalVariableReferenced {

  private static Object o;

  public static Object getInstance(int i) {
    if (o == null) {
      <warning descr="Lazy initialization of 'static' field 'o' is not thread-safe">o<caret></warning> = "" + i;
    }
    return o;
  }
}