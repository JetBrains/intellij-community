package ru.adelf.idea.dotenv.tests.usages;

import ru.adelf.idea.dotenv.tests.DotEnvLightCodeInsightFixtureTestCase;

public class GoUsagesTest extends DotEnvLightCodeInsightFixtureTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.go"));
    }

    protected String getTestDataPath() {
        return basePath + "usages/fixtures";
    }

    public void testUsages() {
        assertUsagesContains("GO_TEST", "GO_TEST2");
    }
}
