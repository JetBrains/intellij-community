package ru.adelf.idea.dotenv.tests.dotenv;

import ru.adelf.idea.dotenv.indexing.DotEnvKeyValuesIndex;
import ru.adelf.idea.dotenv.indexing.DotEnvKeysIndex;
import ru.adelf.idea.dotenv.tests.DotEnvLightCodeInsightFixtureTestCase;

public class DotEnvFileTest extends DotEnvLightCodeInsightFixtureTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(".env"));
    }

    protected String getTestDataPath() {
        return basePath + "dotenv/fixtures";
    }

    public void testEnvKeys() {
        assertIndexContains(DotEnvKeysIndex.KEY,"TEST", "TEST2", "TEST3", "EMPTY_KEY", "OFFSET_KEY");
    }

    public void testEnvKeyValues() {
        assertIndexContains(DotEnvKeyValuesIndex.KEY,"TEST=1");
        assertIndexContains(DotEnvKeyValuesIndex.KEY,"TEST2=2");
        assertIndexContains(DotEnvKeyValuesIndex.KEY,"TEST3=3");
        assertIndexContains(DotEnvKeyValuesIndex.KEY,"OFFSET_KEY=offset");
    }

    public void testEnvCommentedVars() {
        assertIndexContains(DotEnvKeyValuesIndex.KEY,"COMMENTED_VAR=123");
        assertIndexContains(DotEnvKeyValuesIndex.KEY,"COMMENTED_VAR2=123 #comment");
        assertIndexContains(DotEnvKeyValuesIndex.KEY,"COMMENTED_VAR3=123 #com\\\"ment");

        assertIndexContains(DotEnvKeyValuesIndex.KEY,"COMMENTED_VAR4=1");
    }

    public void testEnvEmptyCommentedVars() {
        assertIndexContains(DotEnvKeyValuesIndex.KEY,"COMMENTED_EMPTY=");
        assertIndexContains(DotEnvKeyValuesIndex.KEY,"COMMENTED_EMPTY2=");
    }

    public void testEnvComments() {
        assertIndexNotContains(DotEnvKeysIndex.KEY,"Comment", "#Comment", "#Another comment");
    }

    public void testSlashInTheEndOfQuoted() {
        assertIndexContains(DotEnvKeysIndex.KEY,"SLASH_IN_THE_END_OF_QUOTED", "AFTER");

        assertIndexContains(DotEnvKeyValuesIndex.KEY,"SLASH_IN_THE_END_OF_QUOTED=123 #com\\\\", "AFTER=1");
    }

    public void testMultiLine() {
        assertIndexContains(DotEnvKeyValuesIndex.KEY,"MULTI_LINE=MULTI...");
    }

    public void testEnvExportKeys() {
        assertIndexContains(DotEnvKeysIndex.KEY,"EXPORTED");
    }
}
