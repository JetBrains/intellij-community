class Demo {
  public static void main(String[] args) {
    OUTER:
    for (String arg: args) {
      if (arg == null) <warning descr="Unnecessary label on 'continue' statement">continue</warning> OUTER;
      for(int i=0; i<10; i++) {
        if (arg == null) continue OUTER;
      }
      switch(arg) {
        case "foo": <warning descr="Unnecessary label on 'continue' statement">continue</warning> OUTER;
        case "bar":
          System.out.println("hello");
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + arg);
      }
    }
  }
}