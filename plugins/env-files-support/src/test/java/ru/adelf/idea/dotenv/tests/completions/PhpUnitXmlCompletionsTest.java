package ru.adelf.idea.dotenv.tests.completions;

import org.junit.Test;

public class PhpUnitXmlCompletionsTest extends BaseCompletionsTest {
    @Test
    public void testCompletion() {
        myFixture.configureByFile("phpunit.xml");

        assertEnvCompletions();
    }
}
