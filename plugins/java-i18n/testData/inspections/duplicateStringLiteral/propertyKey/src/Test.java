import org.jetbrains.annotations.*;

@SuppressWarnings({"HardCodedStringLiteral"})
class Test {
  @SuppressWarnings({"HardCodedStringLiteral"})
  String s = "abcdefghhijklmnop";

  @SuppressWarnings({"HardCodedStringLiteral"})
  void f(@PropertyKey(resourceBundle = "xxx.yyy") String key) {
     String s = "abcdefghhijklmnop";
     f("abcdefghhijklmnop"); //no way
  }
}