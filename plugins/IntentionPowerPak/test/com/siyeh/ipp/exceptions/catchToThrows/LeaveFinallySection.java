class C {
  void f() {
    try {
      System.out.println();
    }
    cat<caret>ch (Exception exception) {
      exception.printStackTrace();
    }
    finally {
      System.out.println();
    }
  }
}