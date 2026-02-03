class MyJavaClass {
  public static void main(String[] args) {
    for (MyEnum e : MyEnum.values()) {
      switch (e) {
        case E1:
          System.out.println("E1");
          break;
        case <error descr="Cannot resolve symbol 'E4'">E4</error>:
          System.out.println("fail");
      }
    }
  }
}
