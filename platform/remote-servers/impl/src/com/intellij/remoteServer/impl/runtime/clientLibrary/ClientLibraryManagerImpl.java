// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.clientLibrary;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.remoteServer.runtime.clientLibrary.ClientLibraryDescription;
import com.intellij.remoteServer.runtime.clientLibrary.ClientLibraryManager;
import com.intellij.util.download.*;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * @author michael.golubev
 */
@State(name = "RemoteServerClientLibraries", storages = @Storage("remote-server-client-libraries.xml"))
public class ClientLibraryManagerImpl extends ClientLibraryManager implements PersistentStateComponent<ClientLibraryManagerImpl.State> {
  private Map<String, List<File>> myFiles = new LinkedHashMap<>();


  @Override
  public State getState() {
    State result = new State();
    for (Map.Entry<String, List<File>> entry : myFiles.entrySet()) {
      DownloadedLibraryState libraryState = new DownloadedLibraryState();
      libraryState.myId = entry.getKey();
      for (File file : entry.getValue()) {
        libraryState.myPaths.add(FileUtil.toSystemIndependentName(file.getAbsolutePath()));
      }
      result.myLibraries.add(libraryState);
    }
    return result;
  }

  @Override
  public void loadState(@NotNull State state) {
    myFiles = new HashMap<>();
    for (DownloadedLibraryState libraryState : state.myLibraries) {
      List<File> files = new ArrayList<>();
      for (String path : libraryState.myPaths) {
        files.add(new File(path));
      }
      myFiles.put(libraryState.myId, files);
    }
  }

  @NotNull
  private List<File> getExistentFiles(ClientLibraryDescription description) {
    List<File> files = myFiles.get(description.getId());
    if (files == null) {
      return Collections.emptyList();
    }
    List<File> existentFiles = new ArrayList<>();
    for (File file : files) {
      if (file.exists()) {
        existentFiles.add(file);
      }
    }
    return existentFiles;
  }

  private static File getStoreDirectory(ClientLibraryDescription description) {
    return new File(PathManager.getSystemPath(), "remote-server-libraries/" + description.getId());
  }

  @NotNull
  @Override
  public List<File> download(@NotNull final ClientLibraryDescription libraryDescription) throws IOException {
    List<File> existentFiles = getExistentFiles(libraryDescription);
    if (!existentFiles.isEmpty()) {
      return existentFiles;
    }

    final DownloadableFileService downloadService = DownloadableFileService.getInstance();

    URL versionsUrl = libraryDescription.getDescriptionUrl();
    final DownloadableFileSetVersions<DownloadableFileSetDescription> versions = downloadService.createFileSetVersions(null, versionsUrl);

    List<DownloadableFileSetDescription> descriptions = versions.fetchVersions();
    if (descriptions.isEmpty()) {
      throw new IOException("No client library versions loaded");
    }

    FileDownloader downloader = downloadService.createDownloader(descriptions.get(0));
    List<Pair<File, DownloadableFileDescription>> downloaded = downloader.download(getStoreDirectory(libraryDescription));

    List<File> files = myFiles.get(libraryDescription.getId());
    if (files == null) {
      files = new ArrayList<>();
      myFiles.put(libraryDescription.getId(), files);
    }
    for (Pair<File, DownloadableFileDescription> pair : downloaded) {
      files.add(pair.getFirst());
    }


    return files;
  }

  @Tag("client-library")
  public static class DownloadedLibraryState {
    @Attribute("id")
    public String myId;

    @Property(surroundWithTag = false)
    @XCollection(elementName = "file", valueAttributeName = "path")
    public List<String> myPaths = new ArrayList<>();
  }

  public static class State {
    @Property(surroundWithTag = false)
    @XCollection
    public List<DownloadedLibraryState> myLibraries = new ArrayList<>();
  }
}
