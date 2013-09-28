public class Sup {

    protected final String field

    public Sup() {
        try {
          field = (String)"text";
        }
        catch (RuntimeException e) {
          throw new RuntimeException();
        }
    }
}

class ExtractSuperClass extends Sup {

    public ExtractSuperClass() {
        super();


    }
}
