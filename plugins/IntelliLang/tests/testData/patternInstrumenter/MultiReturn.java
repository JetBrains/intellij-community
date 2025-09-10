import org.intellij.lang.annotations.Pattern;

public class MultiReturn {
  @Pattern("\\d+")
  public String multiReturn(int i) {
    if (i == 1) return "+";
    switch (i) {
      case 2: return "-";
    }
    return "=";
  }
}