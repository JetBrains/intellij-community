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