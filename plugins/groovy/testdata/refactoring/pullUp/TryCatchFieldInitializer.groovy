public class Sup {

}

class ExtractSuperClass extends Sup {

  private final String <caret>field;

  public ExtractSuperClass() {


    try {
      field = (String)"text";
    }
    catch (RuntimeException e) {
      throw new RuntimeException();
    }
  }
}
