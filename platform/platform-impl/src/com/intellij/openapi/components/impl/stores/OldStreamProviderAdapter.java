package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.options.CurrentUserHolder;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

public final class OldStreamProviderAdapter extends StreamProvider implements CurrentUserHolder {
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
  public void saveContent(@NotNull String fileSpec, @NotNull InputStream content, long size, @NotNull RoamingType roamingType, boolean async) throws IOException {
    if (myRoamingType == roamingType) {
      myProvider.saveContent(fileSpec, content, size, roamingType, async);
    }
  }

  @Nullable
  @Override
  public InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException {
    return myRoamingType == roamingType ? myProvider.loadContent(fileSpec, roamingType) : null;
  }

  @NotNull
  @Override
  public String[] listSubFiles(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    return myRoamingType == roamingType ? myProvider.listSubFiles(fileSpec, roamingType) : ArrayUtil.EMPTY_STRING_ARRAY;
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