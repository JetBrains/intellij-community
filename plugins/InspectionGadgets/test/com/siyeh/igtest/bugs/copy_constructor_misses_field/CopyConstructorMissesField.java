class CopyConstructorMissesField {

  private String name;

  CopyConstructorMissesField(CopyConstructorMissesField other) {
    name = other.name;
  }
}
class Child extends CopyConstructorMissesField {
  private String field1;
  private String field2;

  <warning descr="Copy constructor does not copy field 'field2'">Child</warning>(Child other) {
    super(other);
    field1 = other.field1;
  }
}