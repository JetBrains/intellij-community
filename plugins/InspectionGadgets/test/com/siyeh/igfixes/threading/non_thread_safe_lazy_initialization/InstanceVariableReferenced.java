public class InstanceVariableReferenced {

  private static Object example;
  private String s = "yes";

  public Object getInstance() {
    if (example == null) {
      example<caret> = getString(s);
    }
    return example
  }

  private static String getString(String s) {
    return new String(s);
  }
}