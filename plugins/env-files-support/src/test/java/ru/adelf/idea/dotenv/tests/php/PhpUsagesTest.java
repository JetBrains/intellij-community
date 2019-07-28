package ru.adelf.idea.dotenv.tests.php;

import ru.adelf.idea.dotenv.indexing.DotEnvUsagesIndex;
import ru.adelf.idea.dotenv.tests.DotEnvLightCodeInsightFixtureTestCase;

public class PhpUsagesTest extends DotEnvLightCodeInsightFixtureTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.php"));
    }

    protected String getTestDataPath() {
        return basePath + "php/fixtures";
    }

    public void testUsages() {
        assertIndexContains(DotEnvUsagesIndex.KEY,"TEST", "TEST2");
    }
}
