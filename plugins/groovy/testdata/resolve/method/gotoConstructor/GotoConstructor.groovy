class Xx {
  Xx() {}

  Xx(int x) {
    this();
  }
}
new Xx(1);
new Xx();

new X<caret>(2);
