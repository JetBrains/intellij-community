// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UnknownLibraryKind extends PersistentLibraryKind<UnknownLibraryKind.UnknownLibraryProperties> implements TemporaryLibraryKind {
  private static final Logger LOG = Logger.getInstance(UnknownLibraryKind.class);

  private UnknownLibraryKind(@NotNull String kindId) {
    super(kindId);
  }

  @Override
  public int hashCode() {
    return getKindId().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof UnknownLibraryKind && ((UnknownLibraryKind)obj).getKindId().equals(getKindId());
  }

  @NotNull
  @Override
  public UnknownLibraryProperties createDefaultProperties() {
    return new UnknownLibraryProperties();
  }

  public static UnknownLibraryKind getOrCreate(@NotNull String kindId) {
    LibraryKind kind = LibraryKindRegistry.getInstance().findKindById(kindId);
    if (kind instanceof UnknownLibraryKind) {
      return (UnknownLibraryKind)kind;
    }
    if (kind != null) {
      LOG.error("Trying to create UnknownLibraryKind for known kind " + kind);
    }
    return new UnknownLibraryKind(kindId);
  }

  @Override
  public String toString() {
    return "UnknownLibraryKind:" + getKindId();
  }

  public static class UnknownLibraryProperties extends LibraryProperties<Element> {
    private Element myConfiguration;

    public Element getConfiguration() {
      return myConfiguration;
    }

    public void setConfiguration(Element configuration) {
      myConfiguration = configuration;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof UnknownLibraryProperties)) return false;
      Element configuration = ((UnknownLibraryProperties)obj).myConfiguration;
      if (configuration == null && myConfiguration == null) return true;
      if (configuration == null || myConfiguration == null) return false;
      return JDOMUtil.areElementsEqual(myConfiguration, configuration);
    }

    @Override
    public int hashCode() {
      return JDOMUtil.hashCode(myConfiguration, false);
    }

    @Nullable
    @Override
    public Element getState() {
      return myConfiguration;
    }

    @Override
    public void loadState(@NotNull Element state) {
      myConfiguration = state;
    }
  }
}
