/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.CompletionData;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.InvalidDataException;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;

/**
 * @author ven
 */
public class ReferenceCompletionTest extends CompletionTestBase {

  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/lang/completion/data/reference";

  protected String myNewDocumentText;

  public ReferenceCompletionTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  protected String processFile(PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean prevSettings = settings.LIST_PACKAGES_IN_CODE;
    try {
      settings.LIST_PACKAGES_IN_CODE = false;
      return super.processFile(file);
    } finally {
      settings.LIST_PACKAGES_IN_CODE = prevSettings;
    }
  }

  protected LookupItem[] getAcceptableItems(CompletionData data) throws IncorrectOperationException {
    return getAcceptableItemsImpl(data);
  }

  protected boolean addKeywords(PsiReference ref) {
    return false;
  }

  protected boolean addReferenceVariants(PsiReference ref) {
    return true;
  }

  public static Test suite() {
    return new ReferenceCompletionTest();
  }

  protected IdeaProjectTestFixture createFixture() {
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder = factory.createFixtureBuilder();
    builder.addModule(JavaModuleFixtureBuilder.class).addJdk(TestUtils.getMockJdkHome());
    return builder.getFixture();
  }
}