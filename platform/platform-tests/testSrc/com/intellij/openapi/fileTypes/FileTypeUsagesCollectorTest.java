/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes;

import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * @author Nikolay Matveev
 */
public class FileTypeUsagesCollectorTest extends LightPlatformCodeInsightFixtureTestCase {

  private void doTest(@NotNull Collection<FileType> fileTypes) throws CollectUsagesException {
    final Set<UsageDescriptor> usages = new FileTypeUsagesCollector().getProjectUsages(getProject());
    for (UsageDescriptor usage : usages) {
      assertEquals(1, usage.getValue());
    }
    assertEquals(
      ContainerUtil.map2Set(fileTypes, new NotNullFunction<FileType, String>() {
        @NotNull
        @Override
        public String fun(FileType fileType) {
          return fileType.getName();
        }
      }),
      ContainerUtil.map2Set(usages, new NotNullFunction<UsageDescriptor, String>() {
        @NotNull
        @Override
        public String fun(UsageDescriptor usageDescriptor) {
          return usageDescriptor.getKey();
        }
      })
    );
  }

  public void testEmptyProject() throws CollectUsagesException {
    doTest(Arrays.asList());
  }

  public void testSingleFileProject() throws CollectUsagesException {
    myFixture.configureByText("a.txt", "");
    doTest(Arrays.asList(PlainTextFileType.INSTANCE));
  }

  public void testSeveralSameFilesProject() throws CollectUsagesException {
    myFixture.configureByText("a.txt", "");
    myFixture.configureByText("b.txt", "");
    doTest(Arrays.asList(PlainTextFileType.INSTANCE));
  }
}
