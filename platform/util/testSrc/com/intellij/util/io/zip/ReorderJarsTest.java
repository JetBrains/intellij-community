package com.intellij.util.io.zip;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 7/12/11
 */
public class ReorderJarsTest extends TestCase {

  private File myTempDirectory;

  public void testReordering() throws IOException {

    String path = PathManager.getHomePath().replace(File.separatorChar, '/') + "/community/platform/util/testData/reorderJars";
    JBZipFile zipFile = null;
    try {
      zipFile = new JBZipFile(path + "/annotations.jar");
      List<JBZipEntry> entries = zipFile.getEntries();
      System.out.println(entries);
    }
    finally {
      if (zipFile != null) {
        zipFile.close();
      }
    }


    ReorderJarsMain.main(new String[] { path + "/order.txt", path, myTempDirectory.getPath() } );
    File[] files = myTempDirectory.listFiles();
    assertEquals(1, files.length);
    File file = files[0];
    assertEquals("annotations.jar", file.getName());
    try {
      zipFile = new JBZipFile(file);
      List<JBZipEntry> entries = zipFile.getEntries();
      System.out.println(entries);
      assertEquals("org/jetbrains/annotations/Nullable.class", entries.get(0).getName());
      assertEquals("org/jetbrains/annotations/NotNull.class", entries.get(1).getName());
      assertEquals("META-INF/", entries.get(2).getName());
    }
    finally {
      zipFile.close();
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempDirectory = FileUtil.createTempDirectory("__", "__");
  }
}
