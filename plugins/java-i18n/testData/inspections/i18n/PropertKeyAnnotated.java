class Bar {
  void foo(@org.jetbrains.annotations.PropertyKey(resourceBundle="Bar.properties") String key) {}
  {
    foo("key1.key2");
  }
}