import org.junit.jupiter.api.*;


class WithRepeatedInfoAndTest {

  @BeforeEach
  void beforeEach(RepetitionInfo <warning descr="RepetitionInfo won't be injected for @Test methods">repetitionInfo</warning>) { }

  @Test
  void nonRepeated(RepetitionInfo <warning descr="RepetitionInfo is injected for @RepeatedTest only">repetitionInfo</warning>) { }

}

class WithRepeated {
  @RepeatedTest(1)
  void repeatedTestNoParams() { }

  @RepeatedTest(1)
  void repeatedTestWithRepetitionInfo(RepetitionInfo repetitionInfo) { }

  @BeforeAll
  void beforeAllWithRepititionInfo(RepetitionInfo <warning descr="RepetitionInfo is injected for @BeforeEach/@AfterEach only, but not for BeforeAll">repetitionInfo</warning>) {}

  @BeforeEach
  void config(RepetitionInfo repetitionInfo) { }
}

class WithRepeatedAndTests {
  <warning descr="Suspicious combination @Test and @RepeatedTest">@Test</warning>
  @RepeatedTest(1)
  void repeatedTestAndTest() { }
}

class WithParameterized {
  @ParameterizedTest
  void testaccidentalRepetitionInfo(Object s, RepetitionInfo repetitionInfo) { }
}
class WithRepeatedAndCustomNames {
  @RepeatedTest(value = 1, name = "{displayName} {currentRepetition}/{totalRepetitions}")
  void repeatedTestWithCustomName() { }
}

class WithRepeatedAndTestInfo {
  @BeforeEach
  void beforeEach(TestInfo testInfo, RepetitionInfo repetitionInfo) {}

  @RepeatedTest(1)
  void repeatedTestWithTestInfo(TestInfo testInfo) { }

  @AfterEach
  void afterEach(TestInfo testInfo, RepetitionInfo repetitionInfo) {}
}

class WithRepeatedAndTestReporter {
  @BeforeEach
  void beforeEach(TestReporter testReporter, RepetitionInfo repetitionInfo) {}

  @RepeatedTest(1)
  void repeatedTestWithTestInfo(TestReporter testReporter) { }

  @AfterEach
  void afterEach(TestReporter testReporter, RepetitionInfo repetitionInfo) {}
}