// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ThrowableRunnable;
import kotlin.NotImplementedError;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

/**
 * This class isn't used in the new implementation of project model, which is based on {@link com.intellij.workspaceModel.ide Workspace Model}.
 * It shouldn't be used directly, its base class {@link ModuleRootManagerEx} should be used instead. If absolutely needed, its instance must be
 * taken from {@link ModuleRootManager#getInstance(Module)} under instanceof check.
 */
@ApiStatus.Internal
public class ModuleRootManagerImpl extends ModuleRootManagerEx implements Disposable {
  protected static final Logger LOG = Logger.getInstance(ModuleRootManagerImpl.class);

  private final Module myModule;
  private final ProjectRootManagerImpl myProjectRootManager;
  private final VirtualFilePointerManager myFilePointerManager;
  protected RootModelImpl myRootModel;
  private boolean myIsDisposed;
  private boolean myLoaded;
  private final OrderRootsCache myOrderRootsCache;
  private final Map<RootModelImpl, Throwable> myModelCreations = new HashMap<>();

  protected final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();

  public ModuleRootManagerImpl(@NotNull Module module) {
    myModule = module;
    myProjectRootManager = ProjectRootManagerImpl.getInstanceImpl(module.getProject());
    myFilePointerManager = VirtualFilePointerManager.getInstance();

    myRootModel = new RootModelImpl(this, myProjectRootManager, myFilePointerManager);
    myOrderRootsCache = new OrderRootsCache(module);
    MODULE_EXTENSION_NAME.getPoint(module).addExtensionPointListener(new ExtensionPointListener<ModuleExtension>() {
      @Override
      public void extensionAdded(@NotNull ModuleExtension extension, @NotNull PluginDescriptor pluginDescriptor) {
        myRootModel.addModuleExtension(extension);
      }

      @Override
      public void extensionRemoved(@NotNull ModuleExtension extension, @NotNull PluginDescriptor pluginDescriptor) {
        myRootModel.removeModuleExtension(extension);
      }
    }, false, null);
  }

  @Override
  @NotNull
  public Module getModule() {
    return myModule;
  }

  @Override
  @NotNull
  public ModuleFileIndex getFileIndex() {
    return myModule.getService(ModuleFileIndex.class);
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
    return getModifiableModel(RootConfigurationAccessor.DEFAULT_INSTANCE);
  }

  @Override
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

  @Override
  @TestOnly
  public long getModificationCountForTests() {
    throw new NotImplementedError("Make sense only for persistent root manager");
  }

  void makeRootsChange(@NotNull Runnable runnable) {
    ProjectRootManagerEx projectRootManagerEx = (ProjectRootManagerEx)ProjectRootManager.getInstance(myModule.getProject());
    // IMPORTANT: should be the first listener!
    projectRootManagerEx.makeRootsChange(runnable, false, myModule.isLoaded());
  }

  public RootModelImpl getRootModel() {
    return myRootModel;
  }

  @Override
  public ContentEntry @NotNull [] getContentEntries() {
    return myRootModel.getContentEntries();
  }

  @Override
  public OrderEntry @NotNull [] getOrderEntries() {
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

  void commitModel(@NotNull RootModelImpl rootModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    LOG.assertTrue(rootModel.myModuleRootManager == this);
    LOG.assertTrue(!myIsDisposed);

    boolean changed = rootModel.isChanged();

    ModifiableModuleModel moduleModel = ModuleManager.getInstance(myModule.getProject()).getModifiableModel();
    ModifiableModelCommitter.multiCommit(Collections.singletonList(rootModel), moduleModel);

    if (changed) {
      stateChanged();
    }
  }

  static void doCommit(@NotNull RootModelImpl rootModel) {
    ModuleRootManagerImpl rootManager = (ModuleRootManagerImpl)getInstance(rootModel.getModule());
    LOG.assertTrue(!rootManager.myIsDisposed);
    rootModel.doCommit();
    rootModel.dispose();

    try {
      rootManager.stateChanged();
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public Module @NotNull [] getDependencies() {
    return myRootModel.getModuleDependencies();
  }

  @Override
  public Module @NotNull [] getDependencies(boolean includeTests) {
    return myRootModel.getModuleDependencies(includeTests);
  }

  @Override
  public Module @NotNull [] getModuleDependencies() {
    return myRootModel.getModuleDependencies();
  }

  @Override
  public Module @NotNull [] getModuleDependencies(boolean includeTests) {
    return myRootModel.getModuleDependencies(includeTests);
  }

  @Override
  public boolean isDependsOn(@NotNull Module module) {
    return myRootModel.findModuleOrderEntry(module) != null;
  }

  @Override
  public String @NotNull [] getDependencyModuleNames() {
    return myRootModel.getDependencyModuleNames();
  }

  @Override
  public <T> T getModuleExtension(@NotNull final Class<T> klass) {
    return myRootModel.getModuleExtension(klass);
  }

  @Override
  public <R> R processOrder(@NotNull RootPolicy<R> policy, R initialValue) {
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
  public VirtualFile @NotNull [] getContentRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRoots();
  }

  @Override
  public String @NotNull [] getContentRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRootUrls();
  }

  @Override
  public String @NotNull [] getExcludeRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRootUrls();
  }

  @Override
  public VirtualFile @NotNull [] getExcludeRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRoots();
  }

  @Override
  public String @NotNull [] getSourceRootUrls() {
    return getSourceRootUrls(true);
  }

  @Override
  public String @NotNull [] getSourceRootUrls(boolean includingTests) {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getSourceRootUrls(includingTests);
  }

  @Override
  public VirtualFile @NotNull [] getSourceRoots() {
    return getSourceRoots(true);
  }

  @Override
  public VirtualFile @NotNull [] getSourceRoots(final boolean includingTests) {
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
  public void dropCaches() {
    myOrderRootsCache.clearCache();
  }

  public ModuleRootManagerState getState() {
    if (Registry.is("store.track.module.root.manager.changes", false)) {
      LOG.error("getState, module " + myModule.getName());
    }
    return new ModuleRootManagerState(myRootModel);
  }

  public void loadState(@NotNull ModuleRootManagerState object) {
    loadState(object, myLoaded || myModule.isLoaded());
    myLoaded = true;
  }

  protected void loadState(@NotNull ModuleRootManagerState object, boolean throwEvent) {
    ThrowableRunnable<RuntimeException> r = () -> {
      RootModelImpl newModel = new RootModelImpl(object.getRootModelElement(), this, myProjectRootManager, myFilePointerManager, throwEvent);
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
      if (throwEvent) {
        WriteAction.run(r);
      }
      else {
        ReadAction.run(r);
      }
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void stateChanged() {
    if (Registry.is("store.track.module.root.manager.changes", false)) {
      LOG.error("ModelRootManager state changed");
    }
    myModificationTracker.incModificationCount();
  }

  @Override
  @Nullable
  public ProjectModelExternalSource getExternalSource() {
    return ExternalProjectSystemRegistry.getInstance().getExternalSource(myModule);
  }

  public static final class ModuleRootManagerState implements JDOMExternalizable {
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
