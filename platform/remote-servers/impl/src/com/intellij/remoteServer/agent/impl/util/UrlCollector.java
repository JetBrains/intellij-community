package com.intellij.remoteServer.agent.impl.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author michael.golubev
 */
public class UrlCollector {

  private static final Logger LOG = Logger.getInstance(UrlCollector.class);

  private List<File> myFiles;

  public URL[] collect(@NotNull Collection<File> libraries) {
    List<File> files = collectFiles(libraries);
    URL[] result = new URL[files.size()];
    for (int i = 0; i < files.size(); i++) {
      try {
        result[i] = files.get(i).toURI().toURL();
      }
      catch (MalformedURLException e) {
        LOG.error(e); // should never happen
      }
    }
    return result;
  }

  public List<File> collectFiles(Collection<File> libraries) {
    myFiles = new ArrayList<>();
    for (File library : libraries) {
      if (library.exists()) {
        addFile(library);
        if (library.isDirectory()) {
          addLibraries(library);
        }
      }
    }
    return myFiles;
  }

  private void addLibraries(@NotNull File dir) {
    LOG.debug("addLibraries: " + dir.getAbsolutePath() + ", exists: " + dir.exists());
    File[] subFiles = dir.listFiles();
    if (subFiles == null) {
      LOG.warn("Can't list files in " + dir);
      return;
    }

    for (File file : subFiles) {
      if (file.isDirectory()) {
        addLibraries(file);
      }
      else if (file.getName().endsWith(".jar")) {
        addFile(file);
      }
    }
  }

  private void addFile(@NotNull File file) {
    LOG.debug("addFile: " + file.getAbsolutePath() + ", exists: " + file.exists());
    myFiles.add(file);
  }
}
