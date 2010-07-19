/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class CompoundShelfFileProcesor {
  private final String mySubdirName;
  private final StreamProvider[] myServerStreamProviders;
  private static final RoamingType PER_USER = RoamingType.PER_USER;
  private final String FILE_SPEC;
  private final String myShelfPath;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.CompoundShelfFileProcesor");

  public CompoundShelfFileProcesor(final String subdirName) {
    mySubdirName = subdirName;
    myServerStreamProviders = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager().getStreamProviders(PER_USER);

    FILE_SPEC = "$ROOT_CONFIG$/" + subdirName + "/";
    myShelfPath = PathManager.getConfigPath() + File.separator + mySubdirName;
  }

  public CompoundShelfFileProcesor(final StreamProvider[] serverStreamProviders, final String shelfPath) {
    myServerStreamProviders = serverStreamProviders;
    myShelfPath = shelfPath;
    mySubdirName = new File(myShelfPath).getName();
    FILE_SPEC = "$ROOT_CONFIG$/" + mySubdirName + "/";
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

  public List<String> getServerFiles() {
    Collection<String> result = new LinkedHashSet<String>();

    for (StreamProvider serverStreamProvider : myServerStreamProviders) {
      if (serverStreamProvider.isEnabled()) {
        String[] subFiles = serverStreamProvider.listSubFiles(FILE_SPEC);
        ContainerUtil.addAll(result, subFiles);
      }
    }
    return new ArrayList<String>(result);
  }

  public String copyFileFromServer(final String serverFileName, final List<String> localFileNames) {
    for (StreamProvider serverStreamProvider : myServerStreamProviders) {
      if (serverStreamProvider.isEnabled()) {
        try {
          File file = new File(new File(myShelfPath), serverFileName);
          if (!file.exists()) {
            InputStream stream = serverStreamProvider.loadContent(FILE_SPEC + serverFileName, PER_USER);
            if (stream != null) {

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
        catch (IOException e) {
          //ignore
        }
      }
    }
    localFileNames.add(serverFileName);
    return serverFileName;
  }

  public String renameFileOnServer(final String serverFileName, final List<String> serverFileNames, final List<String> localFileNames) {
    String newName = getNewFileName(serverFileName, serverFileNames, localFileNames);
    String oldFilePath = FILE_SPEC + serverFileName;
    String newFilePath = FILE_SPEC + newName;
    for (StreamProvider serverStreamProvider : myServerStreamProviders) {
      renameFileOnProvider(newName, oldFilePath, newFilePath, serverStreamProvider);
    }

    return newName;

  }

  private void renameFileOnProvider(final String newName, final String oldFilePath, final String newFilePath, final StreamProvider serverStreamProvider) {
    if (serverStreamProvider.isEnabled()) {
      try {
        InputStream stream = serverStreamProvider.loadContent(oldFilePath, PER_USER);
        if (stream != null) {
          File file = new File(myShelfPath + "/" + newName);
          copyFileToStream(stream, file);
          serverStreamProvider.deleteFile(oldFilePath,PER_USER);
          copyFileContentToProviders(newFilePath, serverStreamProvider, file);
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
  }

  private void copyFileContentToProviders(final String newFilePath, final StreamProvider serverStreamProvider, final File file)
      throws IOException {
    FileInputStream input = new FileInputStream(file);
    try {
      if (serverStreamProvider.isEnabled()) {
        serverStreamProvider.saveContent(newFilePath, input, file.length(), PER_USER, true);
      }
    }
    finally {
      input.close();
    }
  }

  private void copyFileToStream(final InputStream stream, final File file) throws IOException {
    FileOutputStream out = new FileOutputStream(file);
    try {
      FileUtil.copy(stream, out);
    }
    finally {
      out.close();
    }
  }

  private String getNewFileName(final String serverFileName, final List<String> serverFileNames, final List<String> localFileNames) {
    String name = FileUtil.getNameWithoutExtension(serverFileName);
    String ext = FileUtil.getExtension(serverFileName);
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
    void writeContentTo(Writer writer) throws IOException;
  }

  public void savePathFile(ContentProvider contentProvider, final File patchPath) throws IOException {

    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(patchPath));
    try {
      contentProvider.writeContentTo(writer);
    }
    finally {
      writer.close();
    }



    for (StreamProvider serverStreamProvider : myServerStreamProviders) {
      copyFileContentToProviders(FILE_SPEC + patchPath.getName(), serverStreamProvider, patchPath);
    }


  }

  public File getBaseIODir() {
    return new File(myShelfPath);
  }

  public void saveFile(final File from, final File to) throws IOException {
    for (StreamProvider serverStreamProvider : myServerStreamProviders) {
      copyFileContentToProviders(FILE_SPEC + to.getName(), serverStreamProvider, from);
    }


    FileUtil.copy(from, to);


  }

  public void delete(final String name) {
    FileUtil.delete(new File(getBaseIODir(), name));
    for (StreamProvider serverStreamProvider : myServerStreamProviders) {
      if (serverStreamProvider.isEnabled()) {
        serverStreamProvider.deleteFile(FILE_SPEC + name, PER_USER);
      }
    }

  }
}
