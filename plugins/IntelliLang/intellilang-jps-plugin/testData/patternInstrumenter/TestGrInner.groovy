import org.intellij.lang.annotations.Pattern

@SuppressWarnings("unused")
class TestGrInner {
  TestGrInner(String s1, String s2) {
    new Inner(s1, s2)
  }

  private class Inner {
    Inner(String s1, @Pattern("\\d+") String s2) { }
  }
}