public class StaticVariableReferenced {

  private static Object example;
  private static String s = "yes";

  public static Object getInstance() {
    if (example == null) {
      example<caret> = getString(s);
    }
    return example
  }

  private static String getString(String s) {
    return new String(s);
  }
}