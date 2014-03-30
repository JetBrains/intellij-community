package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.options.CurrentUserHolder;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class OldStreamProviderAdapter extends StreamProvider implements CurrentUserHolder {
  final com.intellij.openapi.options.StreamProvider myProvider;
  private final RoamingType myRoamingType;

  public OldStreamProviderAdapter(com.intellij.openapi.options.StreamProvider provider, @NotNull RoamingType roamingType) {
    myProvider = provider;
    myRoamingType = roamingType;
  }

  @Override
  public boolean isEnabled() {
    return myProvider.isEnabled();
  }

  @Override
  public boolean isApplicable(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    return myRoamingType == roamingType && !(roamingType == RoamingType.PER_USER && StorageUtil.isProjectOrModuleFile(fileSpec));
  }

  @Override
  public boolean saveContent(@NotNull String fileSpec, @NotNull byte[] content, int size, @NotNull RoamingType roamingType, boolean async) throws IOException {
    myProvider.saveContent(fileSpec, new BufferExposingByteArrayInputStream(content, size), size, roamingType, async);
    return false;
  }

  @Nullable
  @Override
  public InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException {
    return myRoamingType == roamingType ? myProvider.loadContent(fileSpec, roamingType) : null;
  }

  @NotNull
  @Override
  public List<String> listSubFiles(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    if (myRoamingType == roamingType) {
      return Arrays.asList(myProvider.listSubFiles(fileSpec, roamingType));
    }
    else {
      return Collections.emptyList();
    }
  }

  @Override
  public void deleteFile(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    if (myRoamingType == roamingType) {
      myProvider.deleteFile(fileSpec, roamingType);
    }
  }

  @Override
  public String getCurrentUserName() {
    return myProvider instanceof CurrentUserHolder ? ((CurrentUserHolder)myProvider).getCurrentUserName() : null;
  }
}