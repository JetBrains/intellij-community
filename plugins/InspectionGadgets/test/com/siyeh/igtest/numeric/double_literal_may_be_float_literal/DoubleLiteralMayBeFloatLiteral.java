class DoubleLiteralMayBeFloatLiteral {

  void literal() {
    System.out.println(<warning descr="'(float)1.1' could be replaced with '1.1f'">(float)1.1</warning>);
    System.out.println(<warning descr="'(float)-7.3' could be replaced with '-7.3f'">(float)-7.3</warning>);
    System.out.println(<warning descr="'(float)-(-((4.2)))' could be replaced with '-(-((4.2f)))'">(float)-(-((4.2)))</warning>);
  }

  void error() {
    <error descr="Incompatible types. Found: 'float', required: 'int'">int i = (float)6.66;</error>
  }
}