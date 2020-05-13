// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
@State(
  name = "FileEditorProviderManager",
  storages = @Storage(value = "fileEditorProviderManager.xml", roamingType = RoamingType.DISABLED)
)
public final class FileEditorProviderManagerImpl extends FileEditorProviderManager
  implements PersistentStateComponent<FileEditorProviderManagerImpl> {

  @Override
  public FileEditorProvider @NotNull [] getProviders(@NotNull final Project project, @NotNull final VirtualFile file) {
    // Collect all possible editors
    List<FileEditorProvider> sharedProviders = new ArrayList<>();
    boolean hideDefaultEditor = false;
    for (final FileEditorProvider provider : FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getExtensionList()) {
      if (ReadAction.compute(() -> {
        if (DumbService.isDumb(project) && !DumbService.isDumbAware(provider)) {
          return false;
        }
        return provider.accept(project, file);
      })) {
        sharedProviders.add(provider);
        hideDefaultEditor |= provider.getPolicy() == FileEditorPolicy.HIDE_DEFAULT_EDITOR;
        if (provider.getPolicy() == FileEditorPolicy.HIDE_DEFAULT_EDITOR && !DumbService.isDumbAware(provider)) {
          String message = "HIDE_DEFAULT_EDITOR is supported only for DumbAware providers; " + provider.getClass() + " is not DumbAware.";
          Logger.getInstance(FileEditorProviderManagerImpl.class).error(PluginException.createByClass(message, null, provider.getClass()));
        }
      }
    }

    // Throw out default editors provider if necessary
    if (hideDefaultEditor) {
      ContainerUtil.retainAll(sharedProviders, provider -> !(provider instanceof DefaultPlatformFileEditorProvider));
    }

    // Sort editors according policies
    sharedProviders.sort(MyComparator.ourInstance);
    return sharedProviders.toArray(new FileEditorProvider[0]);
  }

  @Override
  @Nullable
  public FileEditorProvider getProvider(@NotNull String editorTypeId) {
    for (FileEditorProvider provider : FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getExtensionList()) {
      if (provider.getEditorTypeId().equals(editorTypeId)) {
        return provider;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public FileEditorProviderManagerImpl getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull FileEditorProviderManagerImpl state) {
    mySelectedProviders.clear();
    mySelectedProviders.putAll(state.mySelectedProviders);
  }

  private final Map<String, String> mySelectedProviders = new HashMap<>();

  void providerSelected(@NotNull EditorComposite composite) {
    FileEditorProvider[] providers = composite.getProviders();
    if (providers.length < 2) return;
    mySelectedProviders.put(computeKey(providers),
                            composite.getSelectedWithProvider().getProvider().getEditorTypeId());
  }

  private static @NotNull String computeKey(FileEditorProvider[] providers) {
    return StringUtil.join(ContainerUtil.map(providers, FileEditorProvider::getEditorTypeId), ",");
  }

  @Nullable
  FileEditorProvider getSelectedFileEditorProvider(@NotNull EditorHistoryManager editorHistoryManager,
                                                   @NotNull VirtualFile file,
                                                   FileEditorProvider @NotNull [] providers) {
    FileEditorProvider provider = editorHistoryManager.getSelectedProvider(file);
    if (provider != null || providers.length < 2) {
      return provider;
    }
    String id = mySelectedProviders.get(computeKey(providers));
    return id == null ? null : getProvider(id);
  }

  @MapAnnotation
  public Map<String, String> getSelectedProviders() {
    return mySelectedProviders;
  }

  @SuppressWarnings("unused")
  public void setSelectedProviders(Map<String, String> selectedProviders) {
    mySelectedProviders.clear();
    mySelectedProviders.putAll(selectedProviders);
  }

  @TestOnly
  public void clearSelectedProviders() {
    mySelectedProviders.clear();
  }

  private static final class MyComparator implements Comparator<FileEditorProvider> {
    public static final MyComparator ourInstance = new MyComparator();

    private static double getWeight(FileEditorProvider provider) {
      return provider instanceof WeighedFileEditorProvider
             ? ((WeighedFileEditorProvider)provider).getWeight()
             : Double.MAX_VALUE;
    }

    @Override
    public int compare(FileEditorProvider provider1, FileEditorProvider provider2) {
      int c = provider1.getPolicy().compareTo(provider2.getPolicy());
      if (c != 0) return c;
      final double value = getWeight(provider1) - getWeight(provider2);
      return value > 0 ? 1 : value < 0 ? -1 : 0;
    }
  }
}
