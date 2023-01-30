import java.util.Optional;

public class Several {
  public static void main(String[] args) {
    Optional<String> optional = Optional.ofNullable("1");
    optional.orElseThrow(() -> new RuntimeException());
    optional.orElseThrow<caret>(() -> {
      if (args.length == 1) {
          return new RuntimeException();
      } else {
          return new RuntimeException();
      }
    });
    optional.orElseThrow(() -> {
      try {
        throw new RuntimeException();
      }
      catch (Exception e) {
      }
      try {
          return new ArithmeticException();
      }
      catch (NullPointerException e) {
      }
        return new RuntimeException();
    });
  }
}