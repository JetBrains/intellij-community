class IfCheckBefore {
  void m(Object child, Object parent) {
    if (parent instanceof Number) {
      if (child instanceof String) {
        if ((parent instanceof Integer)) {
          System.out.println(parent);
        }
        else {
          return;
        }
        Integer attribute = (Integer) parent;
      }
    }
  }
}