class PropertyRef {
  public static void main(String[] args) {
    System.out.println(message1("sam<caret>e.name"));
  }
  public static String message1(@org.jetbrains.annotations.PropertyKey(resourceBundle = "Bundle1") String property) {
    return "";
  }
}