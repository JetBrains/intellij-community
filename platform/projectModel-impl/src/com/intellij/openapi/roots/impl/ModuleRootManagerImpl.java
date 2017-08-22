/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ThrowableRunnable;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

public class ModuleRootManagerImpl extends ModuleRootManager implements Disposable {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ModuleRootManagerImpl");

  private final Module myModule;
  private final ProjectRootManagerImpl myProjectRootManager;
  private final VirtualFilePointerManager myFilePointerManager;
  protected RootModelImpl myRootModel;
  private boolean myIsDisposed;
  private boolean myLoaded;
  private final OrderRootsCache myOrderRootsCache;
  private final Map<RootModelImpl, Throwable> myModelCreations = new THashMap<>();

  protected volatile long myModificationCount;

  public ModuleRootManagerImpl(@NotNull Module module,
                               @NotNull ProjectRootManagerImpl projectRootManager,
                               @NotNull VirtualFilePointerManager filePointerManager) {
    myModule = module;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;

    myRootModel = new RootModelImpl(this, projectRootManager, filePointerManager);
    myOrderRootsCache = new OrderRootsCache(module);
  }

  @Override
  @NotNull
  public Module getModule() {
    return myModule;
  }

  @Override
  @NotNull
  public ModuleFileIndex getFileIndex() {
    return ModuleServiceManager.getService(myModule, ModuleFileIndex.class);
  }

  @Override
  public void dispose() {
    myRootModel.dispose();
    myIsDisposed = true;

    if (Disposer.isDebugMode()) {
      List<Map.Entry<RootModelImpl, Throwable>> entries;
      synchronized (myModelCreations) {
        entries = new ArrayList<>(myModelCreations.entrySet());
      }
      for (final Map.Entry<RootModelImpl, Throwable> entry : entries) {
        LOG.warn("\n" +
                 "***********************************************************************************************\n" +
                 "***                        R O O T   M O D E L   N O T   D I S P O S E D                    ***\n" +
                 "***********************************************************************************************\n" +
                 "Created at:", entry.getValue());
        entry.getKey().dispose();
      }
    }
  }

  @Override
  @NotNull
  public ModifiableRootModel getModifiableModel() {
    return getModifiableModel(new RootConfigurationAccessor());
  }

  @NotNull
  public ModifiableRootModel getModifiableModel(@NotNull RootConfigurationAccessor accessor) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    final RootModelImpl model = new RootModelImpl(myRootModel, this, true, accessor, myFilePointerManager, myProjectRootManager) {
      @Override
      public void dispose() {
        super.dispose();
        if (Disposer.isDebugMode()) {
          synchronized (myModelCreations) {
            myModelCreations.remove(this);
          }
        }
      }
    };
    if (Disposer.isDebugMode()) {
      synchronized (myModelCreations) {
        myModelCreations.put(model, new Throwable());
      }
    }
    return model;
  }

  void makeRootsChange(@NotNull Runnable runnable) {
    ProjectRootManagerEx projectRootManagerEx = (ProjectRootManagerEx)ProjectRootManager.getInstance(myModule.getProject());
    // IMPORTANT: should be the first listener!
    projectRootManagerEx.makeRootsChange(runnable, false, myModule.isLoaded());
  }

  public RootModelImpl getRootModel() {
    return myRootModel;
  }

  @NotNull
  @Override
  public ContentEntry[] getContentEntries() {
    return myRootModel.getContentEntries();
  }

  @Override
  @NotNull
  public OrderEntry[] getOrderEntries() {
    return myRootModel.getOrderEntries();
  }

  @Override
  public Sdk getSdk() {
    return myRootModel.getSdk();
  }

  @Override
  public boolean isSdkInherited() {
    return myRootModel.isSdkInherited();
  }

  void commitModel(RootModelImpl rootModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    LOG.assertTrue(rootModel.myModuleRootManager == this);

    boolean changed = rootModel.isChanged();

    final Project project = myModule.getProject();
    final ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
    ModifiableModelCommitter.multiCommit(Collections.singletonList(rootModel), moduleModel);

    if (changed) {
      stateChanged();
    }
  }

  static void doCommit(RootModelImpl rootModel) {
    ModuleRootManagerImpl rootManager = (ModuleRootManagerImpl)getInstance(rootModel.getModule());
    LOG.assertTrue(!rootManager.myIsDisposed);
    rootModel.docommit();
    rootModel.dispose();

    try {
      rootManager.stateChanged();
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  @NotNull
  public Module[] getDependencies() {
    return myRootModel.getModuleDependencies();
  }

  @NotNull
  @Override
  public Module[] getDependencies(boolean includeTests) {
    return myRootModel.getModuleDependencies(includeTests);
  }

  @NotNull
  @Override
  public Module[] getModuleDependencies() {
    return myRootModel.getModuleDependencies();
  }

  @NotNull
  @Override
  public Module[] getModuleDependencies(boolean includeTests) {
    return myRootModel.getModuleDependencies(includeTests);
  }

  @Override
  public boolean isDependsOn(Module module) {
    return myRootModel.isDependsOn(module);
  }

  @Override
  @NotNull
  public String[] getDependencyModuleNames() {
    return myRootModel.getDependencyModuleNames();
  }

  @Override
  public <T> T getModuleExtension(@NotNull final Class<T> klass) {
    return myRootModel.getModuleExtension(klass);
  }

  @Override
  public <R> R processOrder(RootPolicy<R> policy, R initialValue) {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.processOrder(policy, initialValue);
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries() {
    return new ModuleOrderEnumerator(myRootModel, myOrderRootsCache);
  }

  public static OrderRootsEnumerator getCachingEnumeratorForType(@NotNull OrderRootType type, @NotNull Module module) {
    return getEnumeratorForType(type, module).usingCache();
  }

  @NotNull
  private static OrderRootsEnumerator getEnumeratorForType(@NotNull OrderRootType type, @NotNull Module module) {
    OrderEnumerator base = OrderEnumerator.orderEntries(module);
    if (type == OrderRootType.CLASSES) {
      return base.exportedOnly().withoutModuleSourceEntries().recursively().classes();
    }
    if (type == OrderRootType.SOURCES) {
      return base.exportedOnly().recursively().sources();
    }
    return base.roots(type);
  }

  @Override
  @NotNull
  public VirtualFile[] getContentRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRoots();
  }

  @Override
  @NotNull
  public String[] getContentRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRootUrls();
  }

  @Override
  @NotNull
  public String[] getExcludeRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRootUrls();
  }

  @Override
  @NotNull
  public VirtualFile[] getExcludeRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRoots();
  }

  @Override
  @NotNull
  public String[] getSourceRootUrls() {
    return getSourceRootUrls(true);
  }

  @NotNull
  @Override
  public String[] getSourceRootUrls(boolean includingTests) {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getSourceRootUrls(includingTests);
  }

  @Override
  @NotNull
  public VirtualFile[] getSourceRoots() {
    return getSourceRoots(true);
  }

  @Override
  @NotNull
  public VirtualFile[] getSourceRoots(final boolean includingTests) {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getSourceRoots(includingTests);
  }

  @NotNull
  @Override
  public List<VirtualFile> getSourceRoots(@NotNull JpsModuleSourceRootType<?> rootType) {
    return myRootModel.getSourceRoots(rootType);
  }

  @NotNull
  @Override
  public List<VirtualFile> getSourceRoots(@NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    return myRootModel.getSourceRoots(rootTypes);
  }

  public void dropCaches() {
    myOrderRootsCache.clearCache();
  }

  public ModuleRootManagerState getState() {
    if (Registry.is("store.track.module.root.manager.changes", false)) {
      LOG.error("getState, module " + myModule.getName());
    }
    return new ModuleRootManagerState(myRootModel);
  }

  public void loadState(ModuleRootManagerState object) {
    loadState(object, myLoaded || myModule.isLoaded());
    myLoaded = true;
  }

  protected void loadState(ModuleRootManagerState object, boolean throwEvent) {
    ThrowableRunnable<RuntimeException> r = () -> {
      final RootModelImpl newModel = new RootModelImpl(object.getRootModelElement(), this, myProjectRootManager, myFilePointerManager, throwEvent);

      if (throwEvent) {
        makeRootsChange(() -> doCommit(newModel));
      }
      else {
        myRootModel.dispose();
        myRootModel = newModel;
      }

      assert !myRootModel.isOrderEntryDisposed();
    };
    try {
      if (throwEvent) WriteAction.run(r);
      else ReadAction.run(r);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void stateChanged() {
    if (Registry.is("store.track.module.root.manager.changes", false)) {
      LOG.error("ModelRootManager state changed");
    }
    myModificationCount++;
  }

  @Override
  @Nullable
  public ProjectModelExternalSource getExternalSource() {
    return ExternalProjectSystemRegistry.getInstance().getExternalSource(myModule);
  }

  public static class ModuleRootManagerState implements JDOMExternalizable {
    private RootModelImpl myRootModel;
    private Element myRootModelElement;

    public ModuleRootManagerState() {
    }

    public ModuleRootManagerState(RootModelImpl rootModel) {
      myRootModel = rootModel;
    }

    @Override
    public void readExternal(Element element) {
      myRootModelElement = element;
    }

    @Override
    public void writeExternal(Element element) {
      myRootModel.writeExternal(element);
    }

    public Element getRootModelElement() {
      return myRootModelElement;
    }
  }
}
