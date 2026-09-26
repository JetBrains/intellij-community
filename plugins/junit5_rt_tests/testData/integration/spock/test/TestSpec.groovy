import spock.lang.Specification

class TestSpec extends Specification {

  def simple() {
    expect:
    1 == 1
    print("Test1")
  }

  def "passing single test"() {
    expect:
    true
  }

  def "failing single test"() {
    expect:
    false
  }

  def "passing multiple-where test"() {
    expect:
    something == something

    where:
    something << (0..1).collect()
  }

  def "failing multiple-where test"() {
    expect:
    something == (something + 1)

    where:
    something << (0..1).collect()
  }
}
