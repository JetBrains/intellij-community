class <warning descr="Utility class 'UtilityClassCanBeEnum' can be 'enum'">UtilityClassCanBeEnum</warning> {

  public static int notReligious() {
    return 1;
  }
}
enum Other {
  ;

  public static void util() {}
}
class Similar {

  public Similar() {
    barbeque();
  }

  public static boolean barbeque() {
    return true;
  }
}
class CompilationUtil {
  public static final String DEFAULT_EXTENSION; // non-constant

  static {DEFAULT_EXTENSION = "hx";}

  {
    String s = "\\.\\." + DEFAULT_EXTENSION;
  }
}
class Z {
  public static String DEFAULT_EXTENSION = "hx"; // non-constant

  {
    String s = "\\.\\." + DEFAULT_EXTENSION;
  }
}
class <warning descr="Utility class 'Second' can be 'enum'">Second</warning> {
  public static final String X;

  static {
    X = "x";
  }

  {
    String s = "y"; // X not referenced
  }
}
class Main {
  public static void main(String[] args) {
    System.out.println("Hello world!");
  }
}