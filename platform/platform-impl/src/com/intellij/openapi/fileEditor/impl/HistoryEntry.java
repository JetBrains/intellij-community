/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.LightFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * `Heavy` entries should be disposed with {@link #destroy()} to prevent leak of VirtualFilePointer
 */
final class HistoryEntry {
  @NonNls static final String TAG = "entry";
  private static final String FILE_ATTR = "file";
  @NonNls private static final String PROVIDER_ELEMENT = "provider";
  @NonNls private static final String EDITOR_TYPE_ID_ATTR = "editor-type-id";
  @NonNls private static final String SELECTED_ATTR_VALUE = "selected";
  @NonNls private static final String STATE_ELEMENT = "state";

  @NotNull private final VirtualFilePointer myFilePointer;
  /**
   * can be null when read from XML
   */
  @Nullable private FileEditorProvider mySelectedProvider;
  @NotNull private final Map<FileEditorProvider, FileEditorState> myProvider2State = new HashMap<>();

  @Nullable private final Disposable myDisposable;

  private HistoryEntry(@NotNull VirtualFilePointer filePointer,
                       @Nullable FileEditorProvider selectedProvider,
                       @Nullable Disposable disposable) {
    myFilePointer = filePointer;
    mySelectedProvider = selectedProvider;
    myDisposable = disposable;
  }

  @NotNull
  static HistoryEntry createLight(@NotNull VirtualFile file,
                                  @NotNull FileEditorProvider[] providers,
                                  @NotNull FileEditorState[] states,
                                  @NotNull FileEditorProvider selectedProvider) {
    VirtualFilePointer pointer = new LightFilePointer(file);
    HistoryEntry entry = new HistoryEntry(pointer, selectedProvider, null);
    for (int i = 0; i < providers.length; i++) {
      entry.putState(providers[i], states[i]);
    }
    return entry;
  }

  @NotNull
  static HistoryEntry createLight(@NotNull Project project, @NotNull Element e) throws InvalidDataException {
    EntryData entryData = parseEntry(project, e);

    VirtualFilePointer pointer = new LightFilePointer(entryData.url);
    HistoryEntry entry = new HistoryEntry(pointer, entryData.selectedProvider, null);
    for (Pair<FileEditorProvider, FileEditorState> state : entryData.providerStates) {
      entry.putState(state.first, state.second);
    }
    return entry;
  }

  @NotNull
  static HistoryEntry createHeavy(@NotNull Project project,
                                  @NotNull VirtualFile file,
                                  @NotNull FileEditorProvider[] providers,
                                  @NotNull FileEditorState[] states,
                                  @NotNull FileEditorProvider selectedProvider) {
    if (project.isDisposed()) return createLight(file, providers, states, selectedProvider);

    Disposable disposable = Disposer.newDisposable();
    VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(file, disposable, null);

    HistoryEntry entry = new HistoryEntry(pointer, selectedProvider, disposable);
    for (int i = 0; i < providers.length; i++) {
      FileEditorProvider provider = providers[i];
      FileEditorState state = states[i];
      if (provider != null && state != null) {
        entry.putState(provider, state);
      }
    }
    return entry;
  }

  @NotNull
  static HistoryEntry createHeavy(@NotNull Project project, @NotNull Element e) throws InvalidDataException {
    if (project.isDisposed()) return createLight(project, e);

    EntryData entryData = parseEntry(project, e);

    Disposable disposable = Disposer.newDisposable();
    VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(entryData.url, disposable, null);

    HistoryEntry entry = new HistoryEntry(pointer, entryData.selectedProvider, disposable);
    for (Pair<FileEditorProvider, FileEditorState> state : entryData.providerStates) {
      entry.putState(state.first, state.second);
    }
    return entry;
  }


  @NotNull
  public VirtualFilePointer getFilePointer() {
    return myFilePointer;
  }

  @Nullable
  public VirtualFile getFile() {
    return myFilePointer.getFile();
  }

  public FileEditorState getState(@NotNull FileEditorProvider provider) {
    return myProvider2State.get(provider);
  }

  void putState(@NotNull FileEditorProvider provider, @NotNull FileEditorState state) {
    myProvider2State.put(provider, state);
  }

  @Nullable
  FileEditorProvider getSelectedProvider() {
    return mySelectedProvider;
  }

  void setSelectedProvider(@Nullable FileEditorProvider value) {
    mySelectedProvider = value;
  }

  public void destroy() {
    if (myDisposable != null) Disposer.dispose(myDisposable);
  }

  /**
   * @return element that was added to the {@code element}.
   * Returned element has tag {@link #TAG}. Never null.
   */
  public Element writeExternal(Element element, Project project) {
    Element e = new Element(TAG);
    element.addContent(e);
    e.setAttribute(FILE_ATTR, myFilePointer.getUrl());

    for (final Map.Entry<FileEditorProvider, FileEditorState> entry : myProvider2State.entrySet()) {
      FileEditorProvider provider = entry.getKey();

      Element providerElement = new Element(PROVIDER_ELEMENT);
      if (provider.equals(mySelectedProvider)) {
        providerElement.setAttribute(SELECTED_ATTR_VALUE, Boolean.TRUE.toString());
      }
      providerElement.setAttribute(EDITOR_TYPE_ID_ATTR, provider.getEditorTypeId());
      Element stateElement = new Element(STATE_ELEMENT);
      provider.writeState(entry.getValue(), project, stateElement);

      if (!JDOMUtil.isEmpty(stateElement)) {
        providerElement.addContent(stateElement);
      }

      e.addContent(providerElement);
    }

    return e;
  }

  @NotNull
  private static EntryData parseEntry(@NotNull Project project, @NotNull Element e) {
    if (!e.getName().equals(TAG)) {
      throw new IllegalArgumentException("unexpected tag: " + e);
    }

    String url = e.getAttributeValue(FILE_ATTR);
    List<Pair<FileEditorProvider, FileEditorState>> providerStates = new SmartList<>();
    FileEditorProvider selectedProvider = null;

    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);

    for (Element providerElement : e.getChildren(PROVIDER_ELEMENT)) {
      String typeId = providerElement.getAttributeValue(EDITOR_TYPE_ID_ATTR);
      FileEditorProvider provider = FileEditorProviderManager.getInstance().getProvider(typeId);
      if (provider == null) {
        continue;
      }
      if (Boolean.valueOf(providerElement.getAttributeValue(SELECTED_ATTR_VALUE))) {
        selectedProvider = provider;
      }

      Element stateElement = providerElement.getChild(STATE_ELEMENT);
      if (stateElement == null) {
        throw new InvalidDataException();
      }

      if (file != null) {
        FileEditorState state = provider.readState(stateElement, project, file);
        providerStates.add(Pair.create(provider, state));
      }
    }

    return new EntryData(url, providerStates, selectedProvider);
  }

  private static class EntryData {
    @NotNull private final String url;
    @NotNull private final List<Pair<FileEditorProvider, FileEditorState>> providerStates;
    @Nullable private final FileEditorProvider selectedProvider;

    EntryData(@NotNull String url,
              @NotNull List<Pair<FileEditorProvider, FileEditorState>> providerStates,
              @Nullable FileEditorProvider selectedProvider) {
      this.url = url;
      this.providerStates = providerStates;
      this.selectedProvider = selectedProvider;
    }
  }
}
