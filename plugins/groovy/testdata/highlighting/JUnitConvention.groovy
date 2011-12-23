import junit.framework.TestCase

class SpecialGoodTest extends TestCase {
  class <warning descr="JUnit test class name 'MyVeryInner' doesn't match regex '[A-Z][A-Za-z\d]*Test'">MyVeryInner</warning> extends SpecialGoodTest {}
}
class <warning descr="JUnit test class name 'SpecialBad' doesn't match regex '[A-Z][A-Za-z\d]*Test'">SpecialBad</warning> extends TestCase { }
abstract class <warning descr="Abstract JUnit test class name 'SpecialAbstract' doesn't match regex '[A-Z][A-Za-z\d]*TestCase'">SpecialAbstract</warning> extends TestCase { }
abstract class SpecialAbstractTestCase extends TestCase { }