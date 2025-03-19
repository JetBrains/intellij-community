// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public class CompletionTester {
  enum CheckType {EQUALS, INCLUDES, EXCLUDES}

  private final CodeInsightTestFixture myFixture;

  public CompletionTester(final @NotNull CodeInsightTestFixture fixture) {
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
      if (!variant.isEmpty()) {
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
