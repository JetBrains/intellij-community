class Foo {

  public static void main(String[] args) {
    new Thread(new Runnable() {
        @Override
        public void run() {
            print();
        }
    });
  }

  public static void print() {
    System.out.println("print");
  }

}
