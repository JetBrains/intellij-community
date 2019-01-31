// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @deprecated use {@link com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase}
 * author ven
 */
public abstract class CompletionTestBase extends JavaCodeInsightFixtureTestCase {
  protected void doTest() {
    doTest("");
  }
  protected void doTest(String directory) {
    CamelHumpMatcher.forceStartMatching(myFixture.getTestRootDisposable());
    final List<String> stringList = TestUtils.readInput(getTestDataPath() + "/" + getTestName(true) + ".test");
    if (directory.length()!=0) directory += "/";
    final String fileName = directory + getTestName(true) + "." + getExtension();
    myFixture.addFileToProject(fileName, stringList.get(0));
    myFixture.configureByFile(fileName);

    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;


    StringBuilder result = new StringBuilder();
    try {
      myFixture.completeBasic();

      final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor());
      if (lookup != null) {
        List<LookupElement> items = lookup.getItems();
        if (!addReferenceVariants()) {
          items = ContainerUtil.findAll(items, lookupElement -> {
            final Object o = lookupElement.getObject();
            return !(o instanceof PsiMember) && !(o instanceof GrVariable) && !(o instanceof GroovyResolveResult) && !(o instanceof PsiPackage);
          });
        }
        Collections.sort(items, Comparator.comparing(LookupElement::getLookupString));
        result = new StringBuilder();
        for (LookupElement item : items) {
          result.append("\n").append(item.getLookupString());
        }
        result = new StringBuilder(result.toString().trim());
        LookupManager.hideActiveLookup(myFixture.getProject());
      }

    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true;
    }
    assertEquals(StringUtil.trimEnd(stringList.get(1), "\n"), result.toString());
  }

  protected String getExtension() {
    return "groovy";
  }

  protected boolean addReferenceVariants() {
    return true;
  }
}
