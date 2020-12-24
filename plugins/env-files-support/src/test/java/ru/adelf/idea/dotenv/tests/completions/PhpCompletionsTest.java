package ru.adelf.idea.dotenv.tests.completions;

import com.jetbrains.php.lang.PhpFileType;

public class PhpCompletionsTest extends BaseCompletionsTest {
    public void testEnvFunction() {
        myFixture.configureByText(PhpFileType.INSTANCE, "<?php env('<caret>')");

        assertEnvCompletions();
    }

    public void testGetEnvFunction() {
        myFixture.configureByText(PhpFileType.INSTANCE, "<?php getenv('<caret>')");

        assertEnvCompletions();
    }

    public void testEnvArray() {
        myFixture.configureByText(PhpFileType.INSTANCE, "<?php $_ENV['<caret>'];");

        assertEnvCompletions();
    }

    public void testServerArray() {
        myFixture.configureByText(PhpFileType.INSTANCE, "<?php $_SERVER['<caret>'];");

        assertEnvCompletions();
    }
}
