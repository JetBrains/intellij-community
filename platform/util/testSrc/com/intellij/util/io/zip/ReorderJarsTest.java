package com.intellij.util.io.zip;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.lang.JarMemoryLoader;
import junit.framework.TestCase;
import sun.misc.Resource;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 7/12/11
 */
public class ReorderJarsTest extends TestCase {

  private File myTempDirectory;

  public void testReordering() throws IOException {

    String path = PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') + "/ide/plugins/reorderJars";
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
    byte[] data;
    try {
      zipFile = new JBZipFile(file);
      List<JBZipEntry> entries = zipFile.getEntries();
      System.out.println(entries);
      assertEquals(JarMemoryLoader.SIZE_ENTRY, entries.get(0).getName());
      JBZipEntry entry = entries.get(1);
      data = entry.getData();
      assertEquals(548, data.length);
      assertEquals("org/jetbrains/annotations/Nullable.class", entry.getName());
      assertEquals("org/jetbrains/annotations/NotNull.class", entries.get(2).getName());
      assertEquals("META-INF/", entries.get(3).getName());
    }
    finally {
      zipFile.close();
    }

    JarMemoryLoader loader = JarMemoryLoader.load(file, file.toURI().toURL());
    assertNotNull(loader);
    Resource resource = loader.getResource("org/jetbrains/annotations/Nullable.class");
    assertNotNull(resource);
    assertEquals(548, resource.getContentLength());
    byte[] bytes = resource.getBytes();
    assertTrue(Arrays.equals(data, bytes));
  }

  public void testName() throws Exception {

  }

  public void testPluginXml() throws Exception {
    String path = PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') + "/ide/plugins/reorderJars";

    ReorderJarsMain.main(new String[] { path + "/zkmOrder.txt", path, myTempDirectory.getPath() } );
    File[] files = myTempDirectory.listFiles();
    File file = files[0];
    assertEquals("zkm.jar", file.getName());

    JBZipFile zipFile = new JBZipFile(file);
    try {
      List<JBZipEntry> entries = zipFile.getEntries();
      System.out.println(entries);
      assertEquals(JarMemoryLoader.SIZE_ENTRY, entries.get(0).getName());
      assertEquals("META-INF/plugin.xml", entries.get(1).getName());
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
