// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectStoreOwner;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;

/**
 * @author peter
 */
final class DefaultProject extends UserDataHolderBase implements ProjectEx, ProjectStoreOwner {
  private static final Logger LOG = Logger.getInstance(DefaultProject.class);

  private final DefaultProjectTimed myDelegate = new DefaultProjectTimed(this) {
    @NotNull
    @Override
    Project compute() {
      LOG.assertTrue(!ApplicationManager.getApplication().isDisposeInProgress(), "Application is being disposed!");
      return new ProjectImpl() {
        @Override
        protected void bootstrapPicoContainer(@NotNull String name) {
        }

        @Override
        public boolean isDefault() {
          return true;
        }

        @Override
        public boolean isInitialized() {
          return true; // no startup activities, never opened
        }

        @Nullable
        @Override
        protected String activityNamePrefix() {
          // exclude from measurement because default project initialization is not a sequential activity
          // (so, complicates timeline because not applicable)
          // for now we don't measure default project initialization at all, because it takes only ~10 ms
          return null;
        }

        @NotNull
        @Override
        public synchronized MessageBus getMessageBus() {
          return super.getMessageBus();
        }

        @Override
        protected boolean isComponentSuitable(@NotNull ComponentConfig componentConfig) {
          return super.isComponentSuitable(componentConfig) && componentConfig.isLoadForDefaultProject();
        }

        @Override
        public void init(@Nullable ProgressIndicator indicator) {
          super.bootstrapPicoContainer(TEMPLATE_PROJECT_NAME);
          MutablePicoContainer picoContainer = getPicoContainer();
          // do not leak internal delegate, use DefaultProject everywhere instead
          picoContainer.registerComponentInstance(Project.class, DefaultProject.this);

          registerComponents(PluginManagerCore.getLoadedPlugins());
          createComponents(null);
        }

        @Override
        public String toString() {
          return "Project" +
                 (isDisposed() ? " (Disposed)" : "") +
                 TEMPLATE_PROJECT_NAME;
        }

        @Override
        public boolean equals(Object o) {
          return o instanceof Project && ((Project)o).isDefault();
        }

        @Override
        public int hashCode() {
          return DEFAULT_HASH_CODE;
        }
      };
    }

    @Override
    void init(Project project) {
      ((ProjectImpl)project).init(null);
    }
  };
  private static final int DEFAULT_HASH_CODE = 4; // chosen by fair dice roll. guaranteed to be random. see https://xkcd.com/221/ for details.

  DefaultProject() {
  }

  // make default project facade equal to any other default project facade
  // to enable Map<Project, T>
  @Override
  public boolean equals(Object o) {
    return o instanceof Project && ((Project)o).isDefault();
  }

  @Override
  public int hashCode() {
    return DEFAULT_HASH_CODE;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDelegate);
  }

  @NotNull
  private Project getDelegate() {
    return myDelegate.get();
  }

  public boolean isCached() {
    return myDelegate.isCached();
  }

  @Override
  public void setProjectName(@NotNull String name) {
    throw new IllegalStateException();
  }

  // delegates
  @Override
  @NotNull
  public String getName() {
    return ProjectImpl.TEMPLATE_PROJECT_NAME;
  }

  @Override
  @Deprecated
  public VirtualFile getBaseDir() {
    return null;
  }

  @Override
  @Nullable
  @SystemIndependent
  public String getBasePath() {
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getProjectFile() {
    return null;
  }

  @Override
  @Nullable
  @SystemIndependent
  public String getProjectFilePath() {
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @Override
  @NotNull
  public String getLocationHash() {
    return getName();
  }

  @Override
  public void save() {
    getDelegate().save();
  }

  @Override
  public boolean isOpen() {
    return false;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public boolean isDefault() {
    return true;
  }

  @Override
  @Deprecated
  public BaseComponent getComponent(@NotNull String name) {
    return getDelegate().getComponent(name);
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass) {
    return getDelegate().getComponent(interfaceClass);
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass, T defaultImplementationIfAbsent) {
    return getDelegate().getComponent(interfaceClass, defaultImplementationIfAbsent);
  }

  @Override
  public boolean hasComponent(@NotNull Class interfaceClass) {
    return getDelegate().hasComponent(interfaceClass);
  }

  @Override
  @NotNull
  @Deprecated
  public <T> T[] getComponents(@NotNull Class<T> baseClass) {
    return getDelegate().getComponents(baseClass);
  }

  @Override
  @NotNull
  public PicoContainer getPicoContainer() {
    return getDelegate().getPicoContainer();
  }

  @Override
  @NotNull
  public MessageBus getMessageBus() {
    return getDelegate().getMessageBus();
  }

  @Override
  public boolean isDisposed() {
    return ApplicationManager.getApplication().isDisposed();
  }

  @Override
  @Deprecated
  @NotNull
  public <T> T[] getExtensions(@NotNull ExtensionPointName<T> extensionPointName) {
    return getDelegate().getExtensions(extensionPointName);
  }

  @Override
  @NotNull
  public Condition<?> getDisposed() {
    return ApplicationManager.getApplication().getDisposed();
  }

  @NotNull
  @Override
  public IComponentStore getComponentStore() {
    return ((ProjectStoreOwner)getDelegate()).getComponentStore();
  }
}
