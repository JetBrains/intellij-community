/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleManager;
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

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;


public class ModuleRootManagerImpl extends ModuleRootManager implements ModuleComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ModuleRootManagerImpl");

  private final Module myModule;
  private final ProjectRootManagerImpl myProjectRootManager;
  private final VirtualFilePointerManager myFilePointerManager;
  private RootModelImpl myRootModel;
  private final ModuleFileIndexImpl myFileIndex;
  private boolean myIsDisposed = false;
  private boolean isModuleAdded = false;
  private final OrderRootsCache myOrderRootsCache;
  private final Map<RootModelImpl, Throwable> myModelCreations = new THashMap<RootModelImpl, Throwable>();


  public ModuleRootManagerImpl(Module module,
                               DirectoryIndex directoryIndex,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    myModule = module;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;

    myFileIndex = new ModuleFileIndexImpl(myModule, directoryIndex);

    myRootModel = new RootModelImpl(this, myProjectRootManager, myFilePointerManager);
    myOrderRootsCache = new OrderRootsCache(module);
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public ModuleFileIndex getFileIndex() {
    return myFileIndex;
  }

  @NotNull
  public String getComponentName() {
    return "NewModuleRootManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myRootModel.dispose();
    myIsDisposed = true;

    if (Disposer.isDebugMode()) {
      final Set<Map.Entry<RootModelImpl, Throwable>> entries = myModelCreations.entrySet();
      for (final Map.Entry<RootModelImpl, Throwable> entry : new ArrayList<Map.Entry<RootModelImpl, Throwable>>(entries)) {
        System.err.println("***********************************************************************************************");
        System.err.println("***                        R O O T   M O D E L   N O T   D I S P O S E D                    ***");
        System.err.println("***********************************************************************************************");
        System.err.println("Created at:");
        entry.getValue().printStackTrace(System.err);
        entry.getKey().dispose();
      }
    }
  }



  public VirtualFile getExplodedDirectory() {
    return myRootModel.getExplodedDirectory();
  }

  public String getExplodedDirectoryUrl() {
    return myRootModel.getExplodedDirectoryUrl();
  }

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
          assert !((RootModelComponentBase)entry).isDisposed();
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

  RootModelImpl getRootModel() {
    return myRootModel;
  }

  public ContentEntry[] getContentEntries() {
    return myRootModel.getContentEntries();
  }

  @NotNull
  public OrderEntry[] getOrderEntries() {
    return myRootModel.getOrderEntries();
  }

  public Sdk getSdk() {
    return myRootModel.getSdk();
  }

  public boolean isSdkInherited() {
    return myRootModel.isSdkInherited();
  }

  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    return getCachingEnumeratorForType(type, myModule, false).getRoots();
  }

  @NotNull
  public String[] getUrls(OrderRootType type) {
    return getCachingEnumeratorForType(type, myModule, false).getUrls();
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

  public boolean isDependsOn(Module module) {
    return myRootModel.isDependsOn(module);
  }

  @NotNull
  public String[] getDependencyModuleNames() {
    return myRootModel.getDependencyModuleNames();
  }

  @NotNull
  public VirtualFile[] getRootPaths(final OrderRootType rootType) {
    return myRootModel.getRootPaths(rootType);
  }

  @NotNull
  public String[] getRootUrls(final OrderRootType rootType) {
    return myRootModel.getRootUrls(rootType);
  }

  public <T> T getModuleExtension(final Class<T> klass) {
    return myRootModel.getModuleExtension(klass);
  }

  public <R> R processOrder(RootPolicy<R> policy, R initialValue) {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.processOrder(policy, initialValue);
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries() {
    return new ModuleOrderEnumerator(myRootModel, myOrderRootsCache);
  }

  public static OrderRootsEnumerator getCachingEnumeratorForType(OrderRootType type, Module module, final boolean forOtherModules) {
    return getEnumeratorForType(type, module, forOtherModules).usingCache();
  }

  @NotNull
  private static OrderRootsEnumerator getEnumeratorForType(OrderRootType type, Module module, final boolean forOtherModules) {
    OrderEnumerator base = OrderEnumerator.orderEntries(module);
    if (forOtherModules && (type == OrderRootType.COMPILATION_CLASSES || type == OrderRootType.PRODUCTION_COMPILATION_CLASSES
                  || type == OrderRootType.CLASSES || type == OrderRootType.SOURCES)) {
      base = base.exportedOnly();
    }
    if (type == OrderRootType.CLASSES_AND_OUTPUT) {
      return base.compileOnly().recursively().classes();
    }
    if (type == OrderRootType.COMPILATION_CLASSES) {
      return base.recursively().exportedOnly().classes();
    }
    if (type == OrderRootType.PRODUCTION_COMPILATION_CLASSES) {
      return base.compileOnly().productionOnly().recursively().exportedOnly().classes();
    }
    if (type == OrderRootType.CLASSES) {
      return base.withoutModuleSourceEntries().recursively().exportedOnly().classes();
    }
    if (type == OrderRootType.SOURCES) {
      return base.recursively().exportedOnly().sources();
    }
    if (type.collectFromDependentModules()) {
      return base.recursively().roots(type);
    }
    return base.roots(type);
  }

  @NotNull
  public VirtualFile[] getContentRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRoots();
  }

  @NotNull
  public String[] getContentRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRootUrls();
  }

  @NotNull
  public String[] getExcludeRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRootUrls();
  }

  @NotNull
  public VirtualFile[] getExcludeRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRoots();
  }

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

  @NotNull
  public VirtualFile[] getSourceRoots() {
    return getSourceRoots(true);
  }

  @NotNull
  public VirtualFile[] getSourceRoots(final boolean includingTests) {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getSourceRoots(includingTests);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

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
    try {
      final RootModelImpl newModel = new RootModelImpl(object.getRootModelElement(), this, myProjectRootManager, myFilePointerManager);

      boolean throwEvent = myRootModel != null;

      if (throwEvent) {
        makeRootsChange(new Runnable() {
          public void run() {
            doCommit(newModel);
          }
        });
      }
      else {
        myRootModel = newModel;
      }

      assert !myRootModel.isOrderEntryDisposed();
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public static class ModuleRootManagerState implements JDOMExternalizable {
    private RootModelImpl myRootModel;
    private Element myRootModelElement = null;

    public ModuleRootManagerState() {
    }

    public ModuleRootManagerState(final RootModelImpl rootModel) {
      myRootModel = rootModel;
    }

    public void readExternal(Element element) throws InvalidDataException {
      myRootModelElement = element;
    }

    public void writeExternal(Element element) throws WriteExternalException {
      myRootModel.writeExternal(element);
    }

    public Element getRootModelElement() {
      return myRootModelElement;
    }
  }
}
