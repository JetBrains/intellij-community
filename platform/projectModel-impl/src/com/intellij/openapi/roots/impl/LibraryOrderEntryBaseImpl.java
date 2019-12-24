// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  @author dsl
 */
abstract class LibraryOrderEntryBaseImpl extends OrderEntryBaseImpl implements LibraryOrSdkOrderEntry {
  private static final Logger LOG = Logger.getInstance(LibraryOrderEntryBaseImpl.class);
  final ProjectRootManagerImpl myProjectRootManagerImpl;
  @NotNull protected DependencyScope myScope = DependencyScope.COMPILE;
  @Nullable private RootProvider myCurrentlySubscribedRootProvider;

  LibraryOrderEntryBaseImpl(@NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    super(rootModel);
    myProjectRootManagerImpl = projectRootManager;
  }

  protected final void init() {
    updateFromRootProviderAndSubscribe();
  }

  @Override
  @NotNull
  public VirtualFile[] getFiles(@NotNull OrderRootType type) {
    return getRootFiles(type);
  }

  @Override
  @NotNull
  public String[] getUrls(@NotNull OrderRootType type) {
    LOG.assertTrue(!getRootModel().getModule().isDisposed());
    return getRootUrls(type);
  }

  @NotNull
  @Override
  public VirtualFile[] getRootFiles(@NotNull OrderRootType type) {
    RootProvider rootProvider = getRootProvider();
    return rootProvider == null ? VirtualFile.EMPTY_ARRAY : rootProvider.getFiles(type);
  }

  @Nullable
  protected abstract RootProvider getRootProvider();

  @Override
  @NotNull
  public String[] getRootUrls(@NotNull OrderRootType type) {
    RootProvider rootProvider = getRootProvider();
    return rootProvider == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : rootProvider.getUrls(type);
  }

  @Override
  @NotNull
  public final Module getOwnerModule() {
    return getRootModel().getModule();
  }

  void updateFromRootProviderAndSubscribe() {
    getRootModel().makeExternalChange(() -> resubscribe(getRootProvider()));
  }

  private void resubscribe(RootProvider wrapper) {
    unsubscribe();
    subscribe(wrapper);
  }

  private void subscribe(@Nullable RootProvider wrapper) {
    if (wrapper != null) {
      myProjectRootManagerImpl.subscribeToRootProvider(this, wrapper);
    }
    myCurrentlySubscribedRootProvider = wrapper;
  }


  private void unsubscribe() {
    if (myCurrentlySubscribedRootProvider != null) {
      myProjectRootManagerImpl.unsubscribeFromRootProvider(this, myCurrentlySubscribedRootProvider);
    }
    myCurrentlySubscribedRootProvider = null;
  }

  @Override
  public void dispose() {
    unsubscribe();
    super.dispose();
  }
}
