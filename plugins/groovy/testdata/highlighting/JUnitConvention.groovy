import junit.framework.TestCase

class SpecialGoodTest extends TestCase {
  class MyVeryInner extends SpecialGoodTest {}
}
class <warning descr="Test name 'SpecialBad' doesn't match regex '[A-Z][A-Za-z\d]*Test(s|Case)?|Test[A-Z][A-Za-z\d]*'">SpecialBad</warning> extends TestCase { }
class TestInTheBeginning extends TestCase { }
class WithTests extends TestCase { }
class WithTestCase extends TestCase { }
abstract class <warning descr="Abstract test name 'SpecialAbstract' doesn't match regex '[A-Z][A-Za-z\d]*TestCase'">SpecialAbstract</warning> extends TestCase { }
abstract class SpecialAbstractTestCase extends TestCase { }