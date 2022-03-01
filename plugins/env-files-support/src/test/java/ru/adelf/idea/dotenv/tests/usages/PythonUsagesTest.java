package ru.adelf.idea.dotenv.tests.usages;

import org.junit.Test;

public class PythonUsagesTest extends BaseUsagesTest {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("usages.py"));
    }

    @Test
    public void testUsages() {
        assertUsagesContains("PYTHON_TEST", "PYTHON_TEST2");
    }
}
