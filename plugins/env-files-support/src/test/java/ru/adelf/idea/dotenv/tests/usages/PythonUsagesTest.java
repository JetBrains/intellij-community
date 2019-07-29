package ru.adelf.idea.dotenv.tests.usages;

import ru.adelf.idea.dotenv.tests.DotEnvLightCodeInsightFixtureTestCase;

public class PythonUsagesTest extends DotEnvLightCodeInsightFixtureTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.py"));
    }

    protected String getTestDataPath() {
        return basePath + "usages/fixtures";
    }

    public void testUsages() {
        assertUsagesContains("PYTHON_TEST", "PYTHON_TEST2");
    }
}
