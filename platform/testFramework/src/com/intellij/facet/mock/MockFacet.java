// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.mock;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetRootsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public class MockFacet extends Facet<MockFacetConfiguration> implements FacetRootsProvider {
  private boolean myInitialized;
  private boolean myDisposed;
  private boolean myConfigured;
  private static int constructorCounter;

  public MockFacet(final @NotNull Module module, final String name) {
    this(module, name, new MockFacetConfiguration());
  }

  public MockFacet(final Module module, String name, final MockFacetConfiguration configuration) {
    super(MockFacetType.getInstance(), module, name, configuration, null);
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    constructorCounter += 1;
  }

  @Override
  public void initFacet() {
    myInitialized = true;
  }

  @Override
  public void disposeFacet() {
    myDisposed = true;
  }

  public boolean isConfigured() {
    return myConfigured;
  }

  public void configure() {
    myConfigured = true;
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  public void addRoot(VirtualFile root) {
    getConfiguration().addRoot(root);
    fireFacetChangedEvent();
  }

  private void fireFacetChangedEvent() {
    FacetManager.getInstance(getModule()).facetConfigurationChanged(this);
  }

  public void removeRoot(VirtualFile root) {
    getConfiguration().removeRoot(root);
    fireFacetChangedEvent();
  }

  @Override
  public @NotNull @Unmodifiable Collection<VirtualFile> getFacetRoots() {
    return ContainerUtil.mapNotNull(getConfiguration().getRootUrls(), VirtualFileManager.getInstance()::findFileByUrl);
  }

  public static int getConstructorCounter() {
    return constructorCounter;
  }

  public static void setConstructorCounter(int constructorCounter) {
    MockFacet.constructorCounter = constructorCounter;
  }
}
