import java.util.Optional;

class WarnOptional {

  private Optional<String> go() {
    return <warning descr="Return of 'null'">null</warning>;
  }

  Object give() {
    return null;
  }

  private int[] giveMore() {
    return null;
  }
}