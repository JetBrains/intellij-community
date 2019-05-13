import java.util.Optional;
import java.util.OptionalInt;

class WarnOptional {

  private Optional<String> go() {
    return <warning descr="Return of 'null'">null</warning>;
  }

  public OptionalInt nothing() {
    return <warning descr="Return of 'null'">null</warning>;
  }

  Object give() {
    return null;
  }

  private int[] giveMore() {
    return null;
  }
}