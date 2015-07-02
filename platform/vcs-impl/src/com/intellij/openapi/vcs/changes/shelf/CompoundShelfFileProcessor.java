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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.components.impl.stores.StreamProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CompoundShelfFileProcessor {
  public static final String SHELF_DIR_NAME = "shelf";

  private final String mySubdirName;
  private final StreamProvider myServerStreamProvider;
  private final String FILE_SPEC;
  private final String myShelfPath;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.CompoundShelfFileProcessor");

  public CompoundShelfFileProcessor() {
    this(PathManager.getConfigPath());
  }

  public CompoundShelfFileProcessor(String shelfBaseDirPath) {
    this(((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager().getStreamProvider(),
         shelfBaseDirPath + File.separator + SHELF_DIR_NAME);
  }

  public CompoundShelfFileProcessor(@Nullable StreamProvider serverStreamProvider, String shelfPath) {
    myServerStreamProvider = serverStreamProvider;
    myShelfPath = shelfPath;
    mySubdirName = new File(myShelfPath).getName();
    FILE_SPEC = StoragePathMacros.ROOT_CONFIG +  "/" + mySubdirName + "/";
  }

  /*
  public void onWriteExternal() {
    if (myShelfPath != null) {
      File[] shelfFiles = new File(myShelfPath).listFiles();
      if (shelfFiles != null) {
        for (File shelfFile : shelfFiles) {
          try {
            for (StreamProvider serverStreamProvider : myServerStreamProviders) {
              FileInputStream input = new FileInputStream(shelfFile);
              try {

                serverStreamProvider.saveContent(FILE_SPEC + shelfFile.getName(), input, shelfFile.length(), PER_USER);
              }
              finally {
                input.close();
              }
            }

          }
          catch (IOException e) {
            //ignore
          }
        }
      }
    }
  } */

  public List<String> getLocalFiles() {
    ArrayList<String> result = new ArrayList<String>();

    File[] files = new File(myShelfPath).listFiles();
    if (files != null) {
      for (File file : files) {
        result.add(file.getName());
      }
    }
    return result;
  }

  public Collection<String> getServerFiles() {
    if (myServerStreamProvider == null || !myServerStreamProvider.isEnabled()) {
      return Collections.emptyList();
    }
    return myServerStreamProvider.listSubFiles(FILE_SPEC, RoamingType.PER_USER);
  }

  public String copyFileFromServer(final String serverFileName, final List<String> localFileNames) {
    if (myServerStreamProvider != null && myServerStreamProvider.isEnabled()) {
      try {
        File file = new File(new File(myShelfPath), serverFileName);
        if (!file.exists()) {
          InputStream stream = myServerStreamProvider.loadContent(FILE_SPEC + serverFileName, RoamingType.PER_USER);
          if (stream != null) {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(file);
            try {
              FileUtil.copy(stream, out);
            }
            finally {
              out.close();
              stream.close();
            }
          }
        }
      }
      catch (IOException ignored) {
      }
    }
    localFileNames.add(serverFileName);
    return serverFileName;
  }

  public String renameFileOnServer(final String serverFileName, final Collection<String> serverFileNames, final Collection<String> localFileNames) {
    String newName = getNewFileName(serverFileName, serverFileNames, localFileNames);
    if (myServerStreamProvider != null && myServerStreamProvider.isEnabled()) {
      renameFileOnProvider(newName, FILE_SPEC + serverFileName, FILE_SPEC + newName, myServerStreamProvider);
    }
    return newName;

  }

  private void renameFileOnProvider(@NotNull String newName, @NotNull String oldFilePath, @NotNull String newFilePath, @NotNull StreamProvider serverStreamProvider) {
    if (!serverStreamProvider.isEnabled()) {
      return;
    }

    try {
      InputStream stream = serverStreamProvider.loadContent(oldFilePath, RoamingType.PER_USER);
      if (stream != null) {
        File file = new File(myShelfPath + "/" + newName);
        copyFileToStream(stream, file);
        serverStreamProvider.delete(oldFilePath, RoamingType.PER_USER);
        copyFileContentToProviders(newFilePath, serverStreamProvider, file);
      }
    }
    catch (Throwable e) {
      LOG.info(e);
    }
  }

  private static void copyFileContentToProviders(final String newFilePath, final StreamProvider serverStreamProvider, final File file) throws IOException {
    if (serverStreamProvider.isEnabled() && serverStreamProvider.isApplicable(newFilePath, RoamingType.PER_USER)) {
      byte[] content = FileUtil.loadFileBytes(file);
      serverStreamProvider.saveContent(newFilePath, content, content.length, RoamingType.PER_USER);
    }
  }

  private static void copyFileToStream(final InputStream stream, final File file) throws IOException {
    FileOutputStream out = new FileOutputStream(file);
    try {
      FileUtil.copy(stream, out);
    }
    finally {
      out.close();
    }
  }

  private static String getNewFileName(final String serverFileName, final Collection<String> serverFileNames, Collection<String> localFileNames) {
    String name = FileUtil.getNameWithoutExtension(serverFileName);
    String ext = FileUtilRt.getExtension(serverFileName);
    for (int i = 1; ;i++) {
      String suggestedName = name + i + "." + ext;
      if (!serverFileNames.contains(suggestedName) && !localFileNames.contains(suggestedName)) {
        serverFileNames.add(suggestedName);
        localFileNames.add(suggestedName);
        return suggestedName;
      }
    }
  }

  public interface ContentProvider {
    void writeContentTo(Writer writer, CommitContext commitContext) throws IOException;
  }

  public void savePathFile(ContentProvider contentProvider, final File patchPath, CommitContext commitContext) throws IOException {
    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(patchPath), CharsetToolkit.UTF8_CHARSET);
    try {
      contentProvider.writeContentTo(writer, commitContext);
    }
    finally {
      writer.close();
    }

    if (myServerStreamProvider != null && myServerStreamProvider.isEnabled()) {
      copyFileContentToProviders(FILE_SPEC + patchPath.getName(), myServerStreamProvider, patchPath);
    }
  }

  public File getBaseIODir() {
    return new File(myShelfPath);
  }

  public void saveFile(final File from, final File to) throws IOException {
    if (myServerStreamProvider != null && myServerStreamProvider.isEnabled()) {
      copyFileContentToProviders(FILE_SPEC + to.getName(), myServerStreamProvider, from);
    }
    FileUtil.copy(from, to);
  }

  public void delete(final String name) {
    FileUtil.delete(new File(getBaseIODir(), name));
    if (myServerStreamProvider != null && myServerStreamProvider.isEnabled()) {
      StorageUtil.delete(myServerStreamProvider, FILE_SPEC + name, RoamingType.PER_USER);
    }
  }
}