package ru.adelf.idea.dotenv.tests.dotenv;

import org.junit.Test;
import ru.adelf.idea.dotenv.indexing.DotEnvKeyValuesIndex;
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

    @Test
    public void testEnvKeys() {
        assertIndexContains(DotEnvKeyValuesIndex.KEY, "TEST", "TEST2", "TEST3", "EMPTY_KEY", "OFFSET_KEY");
    }

    @Test
    public void testEnvKeyValues() {
        assertContainsKeyAndValue("TEST", "1");

        assertContainsKeyAndValue("TEST2", "2");
        assertContainsKeyAndValue("TEST3", "3");
        assertContainsKeyAndValue("OFFSET_KEY", "offset");
    }

    @Test
    public void testEnvCommentedVars() {
        assertContainsKeyAndValue("COMMENTED_VAR", "123");
        assertContainsKeyAndValue("COMMENTED_VAR2", "123 #comment");
        assertContainsKeyAndValue("COMMENTED_VAR3", "123 #com\\\"ment");

        assertContainsKeyAndValue("COMMENTED_VAR4", "1");
    }

    @Test
    public void testEnvEmptyCommentedVars() {
        assertContainsKeyAndValue("COMMENTED_EMPTY", "");
        assertContainsKeyAndValue("COMMENTED_EMPTY2", "");
    }

    @Test
    public void testEnvComments() {
        assertIndexNotContains(DotEnvKeyValuesIndex.KEY, "Comment", "#Comment", "#Another comment");
    }

    @Test
    public void testSlashInTheEndOfQuoted() {
        assertIndexContains(DotEnvKeyValuesIndex.KEY, "SLASH_IN_THE_END_OF_QUOTED", "AFTER");

        assertContainsKeyAndValue("SLASH_IN_THE_END_OF_QUOTED", "123 #com\\\\");
        assertContainsKeyAndValue("AFTER", "1");
    }

    @Test
    public void testMultiLine() {
        assertContainsKeyAndValue("MULTI_LINE", "MULTI...");
        assertContainsKeyAndValue("MULTI_LINE_SINGLE", "MULTI...");
    }

    @Test
    public void testMultiLineSlashed() {
        assertContainsKeyAndValue("MULTI_LINE_SLASHED", "MULTI...");
        assertContainsKeyAndValue("MULTI_LINE_SLASHED_SINGLE", "MULTI...");
    }

    @Test
    public void testEnvExportKeys() {
        assertIndexContains(DotEnvKeyValuesIndex.KEY, "EXPORTED");
    }

    @Test
    public void testSingleQuotes() {
        assertContainsKeyAndValue("SINGLE_QUOTE", "1");
        assertContainsKeyAndValue("SINGLE_QUOTE_WITH_COMMENT", "123#comment");
    }
}
