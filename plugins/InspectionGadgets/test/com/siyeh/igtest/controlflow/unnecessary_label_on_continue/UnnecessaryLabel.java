class Demo {
  public static void main(String[] args) {
    OUTER:
    for (String arg: args) {
      if (arg == null) continue <warning descr="Unnecessary label 'OUTER' on continue statement">OUTER</warning>;
      for(int i=0; i<10; i++) {
        if (arg == null) continue OUTER;
      }
      switch(arg) {
        case "foo": continue <warning descr="Unnecessary label 'OUTER' on continue statement">OUTER</warning>;
        case "bar":
          System.out.println("hello");
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + arg);
      }
    }
  }
}