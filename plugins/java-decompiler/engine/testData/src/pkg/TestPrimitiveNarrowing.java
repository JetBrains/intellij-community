package pkg;

class TestPrimitiveNarrowing {

  TestPrimitiveNarrowing(Short value) {
  }

  static void invocations() {
    withInteger(null);
    withShort(null);
    withByte(null);
    new TestPrimitiveNarrowing(null);
  }

  static void withByte(Byte defaultValue) {
  }

  static void withInteger(Integer defaultValue) {
  }

  static void withShort(Short defaultValue) {
  }

}
