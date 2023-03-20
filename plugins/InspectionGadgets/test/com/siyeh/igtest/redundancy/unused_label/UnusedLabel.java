class Demo {
  public static void main(String[] args) {
    OUTER:
    for (String arg: args) {
      if (arg == null) continue;
      <warning descr="Unused label 'INNER'">INNER</warning>:
      for(int i=0; i<10; i++) {
        if (arg == null) continue;
        if ("out".equals(arg)) continue OUTER;
        if ("in".equals(arg)) break OUTER;
      }
    }
  }
}