package com.intellij.mock;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockProject extends MockComponentManager implements ProjectEx {
  public MockProject() {
    super(ApplicationManager.getApplication() != null ? ApplicationManager.getApplication().getPicoContainer() : null);
  }

  public boolean isDefault() {
    return false;
  }

  public void checkUnknownMacros() {
  }

  @NotNull
  public PomModel getModel() {
    return ServiceManager.getService(this, PomModel.class);
  }

  public Condition getDisposed() {
    return new Condition() {
      public boolean value(final Object o) {
        return isDisposed();
      }
    };
  }

  @NotNull
  public IProjectStore getStateStore() {
    return new MockProjectStore();
  }

  public void init() {
  }

  public boolean isOptimiseTestLoadSpeed() {
    return false;
  }

  public void setOptimiseTestLoadSpeed(final boolean optimiseTestLoadSpeed) {
    throw new UnsupportedOperationException("Method setOptimiseTestLoadSpeed not implemented in " + getClass());
  }

  public boolean isOpen() {
    return false;
  }

  public boolean isInitialized() {
    return false;
  }

  public VirtualFile getProjectFile() {
    return null;
  }

  @NotNull
  public String getName() {
    return "";
  }

  @Nullable
  @NonNls
  public String getPresentableUrl() {
    return null;
  }

  @NotNull
  @NonNls
  public String getLocationHash() {
    return "mock";
  }

  @NotNull
  @NonNls
  public String getLocation() {
    throw new UnsupportedOperationException("Method getLocation not implemented in " + getClass());
  }

  @NotNull
  public String getProjectFilePath() {
    return "";
  }

  public VirtualFile getWorkspaceFile() {
    return null;
  }

  @Nullable
  public VirtualFile getBaseDir() {
    return null;
  }

  public void save() {
  }

  public GlobalSearchScope getAllScope() {
    return new MockGlobalSearchScope();
  }

}
