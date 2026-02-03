class PropertyRef {
  public static void main(String[] args) {
    @org.jetbrains.annotations.PropertyKey(resourceBundle = "Bundle1") String property;
    property = "sam<caret>e.name";
  }
}