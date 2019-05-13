class Foo {

  public static void main(String[] args) {
    new Thread((<caret>) -> print());
  }

  public static void print() {
    System.out.println("print");
  }

}
