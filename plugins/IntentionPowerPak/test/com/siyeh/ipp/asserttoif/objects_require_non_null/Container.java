@javax.annotation.ParametersAreNonnullByDefault
public class Test {
  private final String message;

  public Test(String message) {
    this.message = m<caret>essage;
  }
}