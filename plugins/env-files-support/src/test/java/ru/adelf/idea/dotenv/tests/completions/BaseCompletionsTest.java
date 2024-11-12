package ru.adelf.idea.dotenv.tests.completions;

import ru.adelf.idea.dotenv.tests.DotEnvLightCodeInsightFixtureTestCase;

abstract class BaseCompletionsTest extends DotEnvLightCodeInsightFixtureTestCase {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.copyFileToProject(".env");
        myFixture.copyFileToProject(".env.example");
    }

    protected String getTestDataPath() {
        return basePath + "completions/fixtures";
    }

    protected void assertEnvCompletions() {
        assertCompletion("ENV_KEY1", "ENV_KEY2", "ENV_KEY_EXAMPLE");
    }
}
