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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FileEditorProviderManagerImpl extends FileEditorProviderManager {
  private static final FileEditorProvider[] EMPTY_ARRAY=new FileEditorProvider[]{};

  private final ArrayList<FileEditorProvider> myProviders;
  private final ArrayList<FileEditorProvider> mySharedProviderList;

  public FileEditorProviderManagerImpl(FileEditorProvider[] providers) {
    myProviders = new ArrayList<FileEditorProvider>();
    mySharedProviderList = new ArrayList<FileEditorProvider>();

    Extensions.getRootArea().getExtensionPoint(FileEditorProvider.EP_FILE_EDITOR_PROVIDER).addExtensionPointListener(new ExtensionPointListener<FileEditorProvider>() {
      public void extensionAdded(final FileEditorProvider extension, @Nullable final PluginDescriptor pluginDescriptor) {
        registerProvider(extension);
      }

      public void extensionRemoved(final FileEditorProvider extension, @Nullable final PluginDescriptor pluginDescriptor) {
        unregisterProvider(extension);
      }
    });

    for (FileEditorProvider provider : providers) {
      registerProvider(provider);
    }
  }

  @NotNull
  public synchronized FileEditorProvider[] getProviders(@NotNull Project project, @NotNull VirtualFile file){
    // Collect all possible editors
    mySharedProviderList.clear();
    boolean doNotShowTextEditor = false;
    final boolean dumb = DumbService.getInstance(project).isDumb();
    for(int i = myProviders.size() -1 ; i >= 0; i--){
      FileEditorProvider provider=myProviders.get(i);
      if((!dumb || DumbService.isDumbAware(provider)) && provider.accept(project, file)){
        mySharedProviderList.add(provider);
        doNotShowTextEditor |= provider.getPolicy() == FileEditorPolicy.HIDE_DEFAULT_EDITOR;
      }
    }

    // Throw out default editors provider if necessary
    if(doNotShowTextEditor){
      for(int i = mySharedProviderList.size() - 1; i >= 0; i--){
        if(mySharedProviderList.get(i) instanceof TextEditorProvider){
          mySharedProviderList.remove(i);
        }
      }
    }

    // Sort editors according policies
    Collections.sort(mySharedProviderList, MyComparator.ourInstance);

    if(!mySharedProviderList.isEmpty()){
      return mySharedProviderList.toArray(new FileEditorProvider[mySharedProviderList.size()]);
    }
    else{
      return EMPTY_ARRAY;
    }
  }

  @Nullable
  public synchronized FileEditorProvider getProvider(@NotNull String editorTypeId){
    for(int i=myProviders.size()-1;i>=0;i--){
      FileEditorProvider provider=myProviders.get(i);
      if(provider.getEditorTypeId().equals(editorTypeId)){
        return provider;
      }
    }
    return null;
  }

  private void registerProvider(FileEditorProvider provider) {
    String editorTypeId = provider.getEditorTypeId();
    for(int i=myProviders.size()-1;i>=0;i--){
      FileEditorProvider _provider=myProviders.get(i);
      if(editorTypeId.equals(_provider.getEditorTypeId())){
        throw new IllegalArgumentException(
          "attempt to register provider with non unique editorTypeId: "+_provider.getEditorTypeId()
        );
      }
    }
    myProviders.add(provider);
  }

  private void unregisterProvider(FileEditorProvider provider) {
    final boolean b = myProviders.remove(provider);
    assert b;
  }

  private static final class MyComparator implements Comparator<FileEditorProvider>{
    public static final MyComparator ourInstance = new MyComparator();

    private static double getWeight(FileEditorProvider provider) {
      return provider instanceof WeighedFileEditorProvider
             ? ((WeighedFileEditorProvider) provider).getWeight()
             : Double.MAX_VALUE;
    }

    public int compare(FileEditorProvider provider1, FileEditorProvider provider2) {
      final int i1 = provider1.getPolicy().ordinal();
      final int i2 = provider2.getPolicy().ordinal();
      if (i1 != i2) return i1 - i2;
      final double value = getWeight(provider1) - getWeight(provider2);
      return value > 0 ? 1 : value < 0 ? -1 : 0;
    }
  }
}
