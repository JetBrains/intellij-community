class Xx {
  Xx() {}

  Xx(int x) {
    this();
  }
}
new Xx(1);
new X<caret>x();

new Xx(2);
