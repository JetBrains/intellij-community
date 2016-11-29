/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DiffManagerTest {
  @Test
  public void testAdditionalTools() {
    DiffManagerImpl diffManager = new DiffManagerImpl();
    MyDiffTool tool = new MyDiffTool();
    diffManager.registerDiffTool(tool);
    MyDiffRequest request = new MyDiffRequest();
    request.addContent();
    request.addContent();
    request.addContent();
    request.addContent();
    assertTrue(diffManager.getDiffTool().canShow(request));
    assertEquals(1, tool.myCanShowCount);
    diffManager.getDiffTool().show(request);
    assertEquals(2, tool.myCanShowCount);
    assertEquals(1, tool.myShowCount);
  }

  private static class MyDiffTool implements DiffTool {
    public int myCanShowCount = 0;
    public int myShowCount = 0;

    @Override
    public boolean canShow(DiffRequest request) {
      myCanShowCount++;
      return canShowImpl(request);
    }

    @Override
    public DiffViewer createComponent(String title, DiffRequest request, Window window, @NotNull Disposable parentDisposable) {
      return null;
    }

    private static boolean canShowImpl(DiffRequest request) {
      return request.getContents().length == 4;
    }

    @Override
    public void show(DiffRequest request) {
      assertTrue(canShowImpl(request));
      myShowCount++;
    }
  }

  private static class MyDiffRequest extends DiffRequest {
    private final List<String> myContentTitles = new ArrayList<>();
    private final List<DiffContent> myDiffContents = new ArrayList<>();

    public MyDiffRequest() {
      super(null);
    }

    @Override
    public String getWindowTitle() {
      return "title";
    }

    @Override
    public String[] getContentTitles() {
      return ArrayUtil.toStringArray(myContentTitles);
    }

    @NotNull
    @Override
    public DiffContent[] getContents() {
      return myDiffContents.toArray(new DiffContent[myDiffContents.size()]);
    }

    public void addContent(DiffContent content, String title) {
      myDiffContents.add(content);
      myContentTitles.add(title);
    }

    public void addContent() {
      Project project = EasyMock.createMock(Project.class);
      addContent(new BinaryContent(project, ArrayUtil.EMPTY_BYTE_ARRAY, null, FileTypes.UNKNOWN, null), "");
    }
  }
}
