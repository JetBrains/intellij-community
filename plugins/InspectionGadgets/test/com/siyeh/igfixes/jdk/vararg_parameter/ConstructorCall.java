class Inequity {

  <caret>Inequity(String... ss) {
  }

  void m() {
    new Inequity(null, null);
  }
}