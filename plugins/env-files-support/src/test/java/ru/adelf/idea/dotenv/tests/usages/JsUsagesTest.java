package ru.adelf.idea.dotenv.tests.usages;

import ru.adelf.idea.dotenv.tests.DotEnvLightCodeInsightFixtureTestCase;

public class JsUsagesTest extends DotEnvLightCodeInsightFixtureTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.js"));
    }

    protected String getTestDataPath() {
        return basePath + "usages/fixtures";
    }

    public void testUsages() {
        assertUsagesContains("JS_TEST");
    }
}
