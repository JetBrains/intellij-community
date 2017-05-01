package pkg;

import java.io.Serializable;
import java.util.Comparator;

public interface TestUnionType {
  public static Comparator comparingInt() {
    return (Comparator & Serializable)(c1, c2) -> 1;
  }
}
