// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
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

  private final List<FileEditorProvider> myProviders = ContainerUtil.createConcurrentList();

  public FileEditorProviderManagerImpl(@NotNull FileEditorProvider[] providers) {
    Extensions.getRootArea().getExtensionPoint(FileEditorProvider.EP_FILE_EDITOR_PROVIDER).addExtensionPointListener(
      new ExtensionPointListener<FileEditorProvider>() {
        @Override
        public void extensionAdded(@NotNull final FileEditorProvider extension, @Nullable final PluginDescriptor pluginDescriptor) {
          registerProvider(extension);
        }

        @Override
        public void extensionRemoved(@NotNull final FileEditorProvider extension, @Nullable final PluginDescriptor pluginDescriptor) {
          unregisterProvider(extension);
        }
      });

    for (FileEditorProvider provider : providers) {
      registerProvider(provider);
    }
  }

  public FileEditorProviderManagerImpl() {
  }

  @Override
  @NotNull
  public FileEditorProvider[] getProviders(@NotNull final Project project, @NotNull final VirtualFile file) {
    // Collect all possible editors
    List<FileEditorProvider> sharedProviders = new ArrayList<>();
    boolean doNotShowTextEditor = false;
    for (final FileEditorProvider provider : myProviders) {
      if (ReadAction.compute(() -> {
        if (DumbService.isDumb(project) && !DumbService.isDumbAware(provider)) {
          return false;
        }
        return provider.accept(project, file);
      })) {
        sharedProviders.add(provider);
        doNotShowTextEditor |= provider.getPolicy() == FileEditorPolicy.HIDE_DEFAULT_EDITOR;
      }
    }

    // Throw out default editors provider if necessary
    if (doNotShowTextEditor) {
      ContainerUtil.retainAll(sharedProviders, provider -> !(provider instanceof TextEditorProvider));
    }

    // Sort editors according policies
    Collections.sort(sharedProviders, MyComparator.ourInstance);

    return sharedProviders.toArray(new FileEditorProvider[0]);
  }

  @Override
  @Nullable
  public FileEditorProvider getProvider(@NotNull String editorTypeId) {
    for (FileEditorProvider provider : myProviders) {
      if (provider.getEditorTypeId().equals(editorTypeId)) {
        return provider;
      }
    }
    return null;
  }

  private void registerProvider(@NotNull FileEditorProvider provider) {
    String editorTypeId = provider.getEditorTypeId();
    if (getProvider(editorTypeId) != null) {
      throw new IllegalArgumentException("attempt to register provider with non unique editorTypeId: " + editorTypeId);
    }
    myProviders.add(provider);
  }

  private void unregisterProvider(@NotNull FileEditorProvider provider) {
    final boolean b = myProviders.remove(provider);
    assert b;
  }

  @Nullable
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

  void providerSelected(EditorComposite composite) {
    if (!(composite instanceof EditorWithProviderComposite)) return;
    FileEditorProvider[] providers = ((EditorWithProviderComposite)composite).getProviders();
    if (providers.length < 2) return;
    mySelectedProviders.put(computeKey(providers),
                            composite.getSelectedEditorWithProvider().getSecond().getEditorTypeId());
  }

  private static String computeKey(FileEditorProvider[] providers) {
    return StringUtil.join(ContainerUtil.map(providers, FileEditorProvider::getEditorTypeId), ",");
  }

  @Nullable
  FileEditorProvider getSelectedFileEditorProvider(EditorHistoryManager editorHistoryManager,
                                                   VirtualFile file,
                                                   FileEditorProvider[] providers) {
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
      final int i1 = provider1.getPolicy().ordinal();
      final int i2 = provider2.getPolicy().ordinal();
      if (i1 != i2) return i1 - i2;
      final double value = getWeight(provider1) - getWeight(provider2);
      return value > 0 ? 1 : value < 0 ? -1 : 0;
    }
  }
}
