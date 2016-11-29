import org.jetbrains.annotations.*;

@SuppressWarnings({"HardCodedStringLiteral"})
class Test {
  @SuppressWarnings({"HardCodedStringLiteral"})
  String s = <warning descr="Duplicate string literal found in&nbsp;&nbsp;&nbsp;'Test' (in this file)">"abcdefghhijklmnop"</warning>;

  @SuppressWarnings({"HardCodedStringLiteral"})
  void f(@PropertyKey(resourceBundle = "xxx.yyy") String key) {
     String s = <warning descr="Duplicate string literal found in&nbsp;&nbsp;&nbsp;'Test' (in this file)">"abcdefghhijklmnop"</warning>;
     f("abcdefghhijklmnop"); //no way
  }
}