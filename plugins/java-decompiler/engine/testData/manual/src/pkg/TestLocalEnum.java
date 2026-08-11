package pkg;

public class TestLocalEnum {
  public String season() {
    enum Season {
      SPRING,
      WINTER;

      String describe() {
        return name().toLowerCase();
      }
    }

    return Season.SPRING.describe() + Season.WINTER.describe();
  }
}
