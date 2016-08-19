/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ModuleRootManagerImpl extends ModuleRootManager implements ModuleComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ModuleRootManagerImpl");

  private final Module myModule;
  private final ProjectRootManagerImpl myProjectRootManager;
  private final VirtualFilePointerManager myFilePointerManager;
  private RootModelImpl myRootModel;
  private boolean myIsDisposed = false;
  private boolean myLoaded = false;
  private boolean isModuleAdded = false;
  private final OrderRootsCache myOrderRootsCache;
  private final Map<RootModelImpl, Throwable> myModelCreations = new THashMap<>();


  public ModuleRootManagerImpl(Module module,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    myModule = module;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;

    myRootModel = new RootModelImpl(this, myProjectRootManager, myFilePointerManager);
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
  @NotNull
  public String getComponentName() {
    return "NewModuleRootManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    myRootModel.dispose();
    myIsDisposed = true;

    if (Disposer.isDebugMode()) {
      final Set<Map.Entry<RootModelImpl, Throwable>> entries = myModelCreations.entrySet();
      for (final Map.Entry<RootModelImpl, Throwable> entry : new ArrayList<>(entries)) {
        System.err.println("***********************************************************************************************");
        System.err.println("***                        R O O T   M O D E L   N O T   D I S P O S E D                    ***");
        System.err.println("***********************************************************************************************");
        System.err.println("Created at:");
        entry.getValue().printStackTrace(System.err);
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
  public ModifiableRootModel getModifiableModel(final RootConfigurationAccessor accessor) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    final RootModelImpl model = new RootModelImpl(myRootModel, this, true, accessor, myFilePointerManager, myProjectRootManager) {
      @Override
      public void dispose() {
        super.dispose();
        if (Disposer.isDebugMode()) {
          myModelCreations.remove(this);
        }

        for (OrderEntry entry : ModuleRootManagerImpl.this.getOrderEntries()) {
          assert !((RootModelComponentBase)entry).isDisposed() : String.format("%s is not disposed!", entry.getPresentableName());
        }
      }
    };
    if (Disposer.isDebugMode()) {
      myModelCreations.put(model, new Throwable());
    }
    return model;
  }

  void makeRootsChange(@NotNull Runnable runnable) {
    ProjectRootManagerEx projectRootManagerEx = (ProjectRootManagerEx)ProjectRootManager.getInstance(myModule.getProject());
    // IMPORTANT: should be the first listener!
    projectRootManagerEx.makeRootsChange(runnable, false, isModuleAdded);
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

    final Project project = myModule.getProject();
    final ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
    ModifiableModelCommitter.multiCommit(new ModifiableRootModel[]{rootModel}, moduleModel);
  }

  static void doCommit(RootModelImpl rootModel) {
    rootModel.docommit();
    rootModel.dispose();
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
  public <T> T getModuleExtension(final Class<T> klass) {
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

  public static OrderRootsEnumerator getCachingEnumeratorForType(OrderRootType type, Module module) {
    return getEnumeratorForType(type, module).usingCache();
  }

  @NotNull
  private static OrderRootsEnumerator getEnumeratorForType(OrderRootType type, Module module) {
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

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void moduleAdded() {
    isModuleAdded = true;
  }


  public void dropCaches() {
    myOrderRootsCache.clearCache();
  }

  public ModuleRootManagerState getState() {
    return new ModuleRootManagerState(myRootModel);
  }

  public void loadState(ModuleRootManagerState object) {
    loadState(object, myLoaded || isModuleAdded);
    myLoaded = true;
  }

  protected void loadState(ModuleRootManagerState object, boolean throwEvent) {
    AccessToken token = throwEvent ? WriteAction.start() : ReadAction.start();
    try {
      final RootModelImpl newModel = new RootModelImpl(object.getRootModelElement(), this, myProjectRootManager, myFilePointerManager, throwEvent);

      if (throwEvent) {
        makeRootsChange(() -> doCommit(newModel));
      }
      else {
        myRootModel.dispose();
        myRootModel = newModel;
      }

      assert !myRootModel.isOrderEntryDisposed();
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
    finally {
      token.finish();
    }
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
    public void writeExternal(Element element) throws WriteExternalException {
      myRootModel.writeExternal(element);
    }

    public Element getRootModelElement() {
      return myRootModelElement;
    }
  }
}
