package ru.adelf.idea.dotenv.tests.completions;

public class PhpUnitXmlCompletionsTest extends BaseCompletionsTest {
    public void testCompletion() {
        myFixture.configureByFile("phpunit.xml");

        assertEnvCompletions();
    }
}
