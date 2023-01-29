import java.util.Optional;

public class Several {
  public static void main(String[] args) {
    Optional<String> optional = Optional.ofNullable("1");

    optional.orElseThrow(() -> {
      if (args.length == 1) {
          return new RuntimeException();
      } else {
          return<caret> new RuntimeException();
      }
    });
  }
}