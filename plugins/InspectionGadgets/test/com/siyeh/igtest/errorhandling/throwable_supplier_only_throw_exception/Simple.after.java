import java.util.Optional;

public class Simple {
  public static void main(String[] args) {
    Optional<String> optional = Optional.ofNullable("1");

    optional.orElseThrow(() -> new RuntimeExc<caret>eption());
  }
}