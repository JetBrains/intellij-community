package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.BinaryContent;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.util.ArrayUtil;
import junit.framework.TestCase;

import java.util.ArrayList;

public class DiffManagerTest extends TestCase {

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

    private boolean canShowImpl(DiffRequest request) {
      return request.getContents().length == 4;
    }

    @Override
    public void show(DiffRequest request) {
      assertTrue(canShowImpl(request));
      myShowCount++;
    }
  }

  private static class MyDiffRequest extends DiffRequest {
    private final ArrayList<String> myContentTitles = new ArrayList<String>();
    private final ArrayList<DiffContent> myDiffContents = new ArrayList<DiffContent>();

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

    @Override
    public DiffContent[] getContents() {
      return myDiffContents.toArray(new DiffContent[myDiffContents.size()]);
    }

    public void addContent(DiffContent content, String title) {
      myDiffContents.add(content);
      myContentTitles.add(title);
    }

    public void addContent() {
      addContent(new BinaryContent(ArrayUtil.EMPTY_BYTE_ARRAY, null, FileTypes.UNKNOWN), "");
    }
  }
}
