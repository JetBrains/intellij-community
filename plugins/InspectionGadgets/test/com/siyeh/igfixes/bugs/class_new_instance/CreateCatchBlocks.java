class High {
  void g(Class<?> x) {
    try {
      x.<caret>newInstance/*1*/();
    } catch (IllegalAccessException | InstantiationException e) {
      e.printStackTrace();
    }
  }
}