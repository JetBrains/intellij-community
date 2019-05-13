class Base {
  {
    Descendant descendant = new Descendant();
    Base b<caret>ase = (Base) descendant;
    consume(base);
  }

  private static void consume(Base value) {}
  private static void consume(Descendant value) {}
}
class Descendant extends Base {}