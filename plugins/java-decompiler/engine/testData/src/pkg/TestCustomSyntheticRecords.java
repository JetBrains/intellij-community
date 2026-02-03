package pkg;


public class TestCustomSyntheticRecords {

  public record CustomGetter(String a, String a22) {
    @Override
    public String a() {
      return a + "1";
    }
  }

  public record CustomCompactConstructor(String a, String a22) {
    public CustomCompactConstructor {
      if (a.equals("a")) {
        throw new AssertionError();
      }
    }
  }
  public record CustomFullConstructor(String a, String a22) {
    public CustomFullConstructor(String a, String a22) {
      this.a = a;
      this.a22 = a;
    }
  }
}