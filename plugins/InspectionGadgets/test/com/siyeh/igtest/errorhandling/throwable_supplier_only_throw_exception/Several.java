import java.util.Optional;

public class Several {
  public static void main(String[] args) {
    Optional<String> optional = Optional.ofNullable("1");

    optional.orElseThrow(() -> {
      if (args.length == 1) {
        throw new RuntimeException();
      } else {
        <warning descr="Throwable supplier doesn't return any exception">throw<caret></warning> new RuntimeException();
      }
    });
  }
}