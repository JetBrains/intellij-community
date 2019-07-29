package ru.adelf.idea.dotenv.tests.usages;

import ru.adelf.idea.dotenv.tests.DotEnvLightCodeInsightFixtureTestCase;

public class RubyUsagesTest extends DotEnvLightCodeInsightFixtureTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.rb"));
    }

    protected String getTestDataPath() {
        return basePath + "usages/fixtures";
    }

    public void testUsages() {
        assertUsagesContains("RUBY_TEST");
    }
}
