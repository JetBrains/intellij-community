package ru.adelf.idea.dotenv.tests;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.indexing.DotEnvKeyValuesIndex;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copy of LaravelLightCodeInsightFixtureTestCase from laravel plugin
 */
@RunWith(JUnit4.class)
public abstract class DotEnvLightCodeInsightFixtureTestCase extends BasePlatformTestCase {

    protected String basePath = "src/test/java/ru/adelf/idea/dotenv/tests/";

    protected void assertCompletion(String... shouldContain) {
        myFixture.completeBasic();

        List<String> strings = myFixture.getLookupElementStrings();

        if (strings == null) {
            fail("Null completion");
            return;
        }

        assertContainsElements(strings, shouldContain);
    }

    protected void assertIndexContains(@NotNull ID<String, ?> id, @NotNull String... keys) {
        assertIndex(id, false, keys);
    }

    protected void assertIndexNotContains(@NotNull ID<String, ?> id, @NotNull String... keys) {
        assertIndex(id, true, keys);
    }

    private void assertIndex(@NotNull ID<String, ?> id, boolean notCondition, @NotNull String... keys) {
        for (String key : keys) {
            final Collection<VirtualFile> virtualFiles = new ArrayList<>();

            FileBasedIndexImpl.getInstance().getFilesWithKey(id, new HashSet<>(Collections.singletonList(key)), virtualFile -> {
                virtualFiles.add(virtualFile);
                return true;
            }, GlobalSearchScope.allScope(getProject()));

            if (notCondition && virtualFiles.size() > 0) {
                fail(String.format("Fail that ID '%s' not contains '%s'", id, key));
            } else if (!notCondition && virtualFiles.size() == 0) {
                fail(String.format("Fail that ID '%s' contains '%s'", id, key));
            }
        }
    }

    protected void assertUsagesContains(@NotNull String... keys) {
        for (String key : keys) {
            PsiElement[] usages = EnvironmentVariablesApi.getKeyUsages(this.myFixture.getProject(), key);
            if (usages.length == 0) {
                fail(String.format("Fail that usages contains '%s'", key));
            }
        }
    }

    protected void assertContainsKeyAndValue(@NotNull String key, @NotNull String value) {
        assertIndexContains(DotEnvKeyValuesIndex.KEY, key);

        final AtomicBoolean found = new AtomicBoolean(false);
        Set<String> variants = new HashSet<>();

        FileBasedIndexImpl.getInstance().processValues(DotEnvKeyValuesIndex.KEY, key, null, (virtualFile, s) -> {
            variants.add(s);

            if (s.equals(value)) {
                found.set(true);
            }
            return false;
        }, GlobalSearchScope.allScope(myFixture.getProject()));

        if (!found.get()) {
            fail(String.format("Fail that index contains pair '%s' => '%s'. Variants: '%s'", key, value, String.join("', '", variants)));
        }
    }
}
