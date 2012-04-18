package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import com.intellij.util.io.zip.ZipShort;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Nullable;
import sun.misc.Resource;

import java.io.*;
import java.net.URL;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Dmitry Avdeev
 *         Date: 7/12/11
 */
public class JarMemoryLoader {

  public static final String SIZE_ENTRY = "META-INF/jb/$$size$$";
  //private static final Logger LOG = Logger.getInstance(JarMemoryLoader.class);

  private final Map<String, Resource> myResources = new THashMap<String, Resource>();

  public Resource getResource(String entryName) {
    return myResources.remove(entryName);
  }

  @Nullable
  public static JarMemoryLoader load(File file, URL baseUrl) throws IOException {
    FileInputStream inputStream = new FileInputStream(file);
    try {
//      long start = System.currentTimeMillis();
      JarMemoryLoader loader = load(inputStream, baseUrl);
//      if (loader != null) {
//        LOG.info(loader.myResources.size() + " classes from " + file.getName() + " preloaded in " + (System.currentTimeMillis() - start) + " ms");
//      }
      return loader;
    }
    finally {
      inputStream.close();
    }
  }

  @Nullable
  public static JarMemoryLoader load(InputStream inputStream, URL baseUrl) throws IOException {
    ZipInputStream zipStream = new ZipInputStream(inputStream);
    try {
      ZipEntry sizeEntry = zipStream.getNextEntry();
      if (sizeEntry == null || !sizeEntry.getName().equals(SIZE_ENTRY)) return null;
      byte[] bytes = FileUtil.loadBytes(zipStream, 2);
      int size = ZipShort.getValue(bytes);

      JarMemoryLoader loader = new JarMemoryLoader();
      for (int i = 0; i < size; i++) {
        ZipEntry entry = zipStream.getNextEntry();
        if (entry == null) return loader;
        byte[] content = FileUtil.loadBytes(zipStream, (int)entry.getSize());
        MyResource resource = new MyResource(entry.getName(), new URL(baseUrl, entry.getName()), content);
        loader.myResources.put(entry.getName(), resource);
      }
      return loader;
    }
    finally {
      zipStream.close();
    }
  }

  private static class MyResource extends Resource {

    private String myName;
    private URL myUrl;
    private final byte[] myContent;

    public MyResource(String name, URL url, byte[] content) {
      myName = name;
      myUrl = url;
      myContent = content;
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public URL getURL() {
      return myUrl;
    }

    @Override
    public URL getCodeSourceURL() {
      return myUrl;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return new UnsyncByteArrayInputStream(myContent);
    }

    @Override
    public int getContentLength() throws IOException {
      return myContent.length;
    }
  }
}
