import spock.lang.Specification

class TestSpec extends Specification {

  def simple() {
    expect:
    1 == 1
    print("Test1")
  }
}
