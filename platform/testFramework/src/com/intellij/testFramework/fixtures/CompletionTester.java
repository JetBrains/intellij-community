/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
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
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import static org.junit.Assert.assertTrue;

/**
 * User: Andrey.Vokin
 * Date: 2/13/13
 */
public class CompletionTester {
  enum CheckType {EQUALS, INCLUDES, EXCLUDES}

  private final CodeInsightTestFixture myFixture;

  public CompletionTester(@NotNull final CodeInsightTestFixture fixture) {
    myFixture = fixture;
  }

  public void configure(String... files) throws IOException {
    myFixture.configureByFiles(files);
  }

  protected String getDelimiter() {
    return "---";
  }

  public void doTestVariantsInner(String fileName, FileType fileType) throws Throwable {
    String fullFileName = myFixture.getTestDataPath() + File.separator + fileName;
    String content = FileUtil.loadFile(new File(fullFileName));

    int index = content.indexOf(getDelimiter());
    assert index > 0;

    String testFileContent = content.substring(0, index);
    String expectedDataContent = content.substring(index + getDelimiter().length());

    myFixture.configureByText(fileType, testFileContent);
    final Scanner in = new Scanner(expectedDataContent);

    final CompletionType type = CompletionType.valueOf(in.next());
    final int count = in.nextInt();
    final CheckType checkType = CheckType.valueOf(in.next());

    in.useDelimiter("\n");
    final List<String> variants = new ArrayList<>();
    while (in.hasNext()) {
      final String variant = in.next().trim();
      if (variant.length() > 0) {
        variants.add(variant);
      }
    }

    myFixture.complete(type, count);
    List<String> stringList = myFixture.getLookupElementStrings();
    if (stringList == null) {
      stringList = Collections.emptyList();
    }

    if (checkType == CheckType.EQUALS) {
      UsefulTestCase.assertOrderedEquals(stringList, variants);
    }
    else if (checkType == CheckType.INCLUDES) {
      variants.removeAll(stringList);
      assertTrue("Missing variants: " + variants, variants.isEmpty());
    }
    else if (checkType == CheckType.EXCLUDES) {
      variants.retainAll(stringList);
      assertTrue("Unexpected variants: " + variants, variants.isEmpty());
    }
  }
}
