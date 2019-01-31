class X {

  void f(DetailsEnum detailsEnum) {
      switch (detailsEnum) {
          case EDIT:
              break;
          case BASE:
              break;
          case FULL:
              break;
          default:
              throw new RuntimeException();
      }
  }

  enum DetailsEnum {
    EDIT, BASE, FULL
  }
}