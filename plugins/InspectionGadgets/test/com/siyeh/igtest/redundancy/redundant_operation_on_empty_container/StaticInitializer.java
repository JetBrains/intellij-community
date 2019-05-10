import java.util.ArrayList;
import java.util.List;

class Magic {
  private static final List<Magic> VALUES = new ArrayList<Magic>();
  private Magic() {
    VALUES.add(this);
  }
  public static Magic M1 = new Magic();
  public static Magic M2 = new Magic();
  public static Magic M3 = new Magic();
  static {
    for (Magic value : VALUES) { // Was false-positive RedundantOperationOnEmptyContainer on 'VALUES'
      System.out.println(value);
    }
  }
}