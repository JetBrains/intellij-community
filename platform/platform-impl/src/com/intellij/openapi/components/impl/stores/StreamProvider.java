package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.RoamingType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

public abstract class StreamProvider {
  public static final StreamProvider[] EMPTY_ARRAY = new StreamProvider[0];

  public abstract boolean isEnabled();

  /**
   * @param fileSpec
   * @param content bytes of content, size of array is not actual size of data, you must use {@code size}
   * @param size actual size of data
   * @param roamingType
   * @param async
   */
  public abstract boolean saveContent(@NotNull String fileSpec, @NotNull byte[] content, int size, @NotNull RoamingType roamingType, boolean async) throws IOException;

  @Nullable
  public abstract InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException;

  @NotNull
  public Collection<String> listSubFiles(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    return Collections.emptyList();
  }

  public abstract void deleteFile(@NotNull String fileSpec, @NotNull RoamingType roamingType);
}