// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore;

import com.intellij.openapi.components.*;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

@SuppressWarnings("ClassExplicitlyAnnotation")
@Internal
public class FileStorageAnnotation implements Storage {
  protected final String path;

  private final boolean deprecated;
  private final Class<? extends StateSplitterEx> mySplitterClass;

  public static final FileStorageAnnotation PROJECT_FILE_STORAGE_ANNOTATION =
    new FileStorageAnnotation(StoragePathMacros.PROJECT_FILE, false);

  public static final FileStorageAnnotation MODULE_FILE_STORAGE_ANNOTATION =
    new FileStorageAnnotation(StoragePathMacros.MODULE_FILE, false);

  public FileStorageAnnotation(@NotNull String path, boolean deprecated) {
    this(path, deprecated, StateSplitterEx.class);
  }

  public FileStorageAnnotation(@NotNull String path, boolean deprecated, Class<? extends StateSplitterEx> splitterClass) {
    this.path = path;
    this.deprecated = deprecated;
    mySplitterClass = splitterClass;
  }

  @Override
  public ThreeState useSaveThreshold() {
    return ThreeState.UNSURE;
  }

  @Override
  public boolean exclusive() {
    return false;
  }

  @Override
  public boolean exportable() {
    return false;
  }

  @Override
  public boolean usePathMacroManager() {
    return true;
  }

  @Override
  public String file() {
    return value();
  }

  @Override
  public String value() {
    return path;
  }

  @Override
  public boolean deprecated() {
    return deprecated;
  }

  @Override
  public RoamingType roamingType() {
    return RoamingType.DEFAULT;
  }

  @Override
  public Class<? extends StateStorage> storageClass() {
    return StateStorage.class;
  }

  @Override
  public Class<? extends StateSplitterEx> stateSplitter() {
    return mySplitterClass;
  }

  @Override
  public @NotNull Class<? extends Annotation> annotationType() {
    throw new UnsupportedOperationException("Method annotationType not implemented in " + getClass());
  }
}