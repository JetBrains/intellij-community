import static java.lang.Character.toLowerCase;

public class StaticImport {

  void example() {
    "".codePoints().map(Character::toLowerCase);
  }
}