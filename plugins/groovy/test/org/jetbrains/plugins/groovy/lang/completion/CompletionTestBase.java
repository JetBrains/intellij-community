package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.CompositeCompletionData;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;

import java.util.Arrays;
import java.util.List;

/**
 * author ven
 */
public abstract class CompletionTestBase extends JavaCodeInsightFixtureTestCase {

  protected void doTest() throws Throwable {
    final List<String> stringList = SimpleGroovyFileSetTestCase.readInput(getTestDataPath() + "/" + getTestName(true) + ".test");
    final String fileName = getTestName(true) + "." + getExtension();
    myFixture.addFileToProject(fileName, stringList.get(0));
    myFixture.configureByFile(fileName);

    CompositeCompletionData.restrictCompletion(addReferenceVariants(), addKeywords());

    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX = false;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;


    String result = "";
    try {
      myFixture.completeBasic();

      final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor());
      if (lookup != null) {
        LookupElement[] items = lookup.getItems();
        Arrays.sort(items);
        result = "";
        for (LookupElement item : items) {
          result = result + "\n" + item.getLookupString();
        }
        result = result.trim();
        LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();
      }

    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true;
      CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX = old;
      CompositeCompletionData.restrictCompletion(true, true);
    }
    assertEquals(stringList.get(1), result);
  }

  protected String getExtension() {
    return "groovy";
  }

  protected FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  protected boolean addKeywords() {
    return true;
  }

  protected boolean addReferenceVariants() {
    return true;
  }
}
