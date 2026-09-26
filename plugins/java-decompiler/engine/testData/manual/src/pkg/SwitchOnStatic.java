public class SwitchOnStatic {
  public static void main(String[] args) {
    staticStringSelector();
    staticIntSelector();
    staticIntSelectorNotInlined();
  }

  public static void staticStringSelector() {
    switch (getStaticStringSelector()) {
      case "1":
        System.out.println("a");
        break;
      case "2":
        System.out.println("b");
        break;
    }
  }

  public static String getStaticStringSelector() {
    return "1";
  }

  public static void staticIntSelector() {
    switch (getStaticIntSelector()) {
      case 1:
        System.out.println("a");
        break;
      case 2:
        System.out.println("b");
        break;
    }
  }

  public static int getStaticIntSelector() {
    return 1;
  }

  public static void staticIntSelectorNotInlined() {
    int cc = getStaticIntSelector();
    switch (cc) {
      case 1:
        System.out.println("a");
        break;
      case 2:
        System.out.println("b");
        break;
    }
  }
}