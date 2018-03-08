package ru.adelf.idea.dotenv.tests.dotenv;

import ru.adelf.idea.dotenv.indexing.DotEnvKeysIndex;
import ru.adelf.idea.dotenv.tests.DotEnvLightCodeInsightFixtureTestCase;

import java.io.File;

public class DotEnvFileTest extends DotEnvLightCodeInsightFixtureTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(".env"));
    }

    protected String getTestDataPath() {
        return new File(this.getClass().getResource("fixtures").getFile()).getAbsolutePath();
    }

    public void testEnvKeys() {
        assertIndexContains(DotEnvKeysIndex.KEY,"TEST", "TEST2", "EMPTY_KEY", "OFFSET_KEY");
    }
}
