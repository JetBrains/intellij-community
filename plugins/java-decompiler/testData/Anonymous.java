public class Anonymous {
  private int count = 0;

  public Object produce() {
    final int id = count++;
    return new Object() {
      @Override
      public String toString() {
        return "anonymous_" + id;
      }
    };
  }
}