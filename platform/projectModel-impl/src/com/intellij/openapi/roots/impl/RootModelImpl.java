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

import com.intellij.configurationStore.Scheme_implKt;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public class RootModelImpl extends RootModelBase implements ModifiableRootModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.RootModelImpl");

  private final Set<ContentEntry> myContent = new TreeSet<>(ContentComparator.INSTANCE);

  private final List<OrderEntry> myOrderEntries = new Order();
  // cleared by myOrderEntries modification, see Order
  @Nullable private OrderEntry[] myCachedOrderEntries;

  @NotNull private final ModuleLibraryTable myModuleLibraryTable;
  final ModuleRootManagerImpl myModuleRootManager;
  private boolean myWritable;
  private final VirtualFilePointerManager myFilePointerManager;
  private boolean myDisposed;
  private final Set<ModuleExtension> myExtensions = new TreeSet<>((o1, o2) -> Comparing.compare(o1.getClass().getName(),
                                                                                                o2.getClass().getName()));
  @Nullable
  private final Map<ModuleExtension, byte[]> myExtensionToStateDigest;

  private final RootConfigurationAccessor myConfigurationAccessor;

  private final ProjectRootManagerImpl myProjectRootManager;
  // have to register all child disposables using this fake object since all clients just call ModifiableModel.dispose()
  private final CompositeDisposable myDisposable = new CompositeDisposable();

  RootModelImpl(@NotNull ModuleRootManagerImpl moduleRootManager,
                @NotNull ProjectRootManagerImpl projectRootManager,
                @NotNull VirtualFilePointerManager filePointerManager) {
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;

    myWritable = false;

    addSourceOrderEntries();
    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager);

    for (ModuleExtension extension : Extensions.getExtensions(ModuleExtension.EP_NAME, moduleRootManager.getModule())) {
      ModuleExtension model = extension.getModifiableModel(false);
      registerOnDispose(model);
      myExtensions.add(model);
    }
    myConfigurationAccessor = new RootConfigurationAccessor();
    myExtensionToStateDigest = null;
  }

  private void addSourceOrderEntries() {
    myOrderEntries.add(new ModuleSourceOrderEntryImpl(this));
  }

  RootModelImpl(@NotNull Element element,
                @NotNull ModuleRootManagerImpl moduleRootManager,
                @NotNull ProjectRootManagerImpl projectRootManager,
                @NotNull VirtualFilePointerManager filePointerManager, boolean writable) throws InvalidDataException {
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;
    myModuleRootManager = moduleRootManager;

    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager);

    for (Element child : element.getChildren(ContentEntryImpl.ELEMENT_NAME)) {
      myContent.add(new ContentEntryImpl(child, this));
    }

    boolean moduleSourceAdded = false;
    for (Element child : element.getChildren(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME)) {
      final OrderEntry orderEntry = OrderEntryFactory.createOrderEntryByElement(child, this, myProjectRootManager);
      if (orderEntry instanceof ModuleSourceOrderEntry) {
        if (moduleSourceAdded) continue;
        moduleSourceAdded = true;
      }
      myOrderEntries.add(orderEntry);
    }

    if (!moduleSourceAdded) {
      myOrderEntries.add(new ModuleSourceOrderEntryImpl(this));
    }

    myWritable = writable;

    RootModelImpl originalRootModel = moduleRootManager.getRootModel();
    for (ModuleExtension extension : originalRootModel.myExtensions) {
      ModuleExtension model = extension.getModifiableModel(false);

      if (model instanceof PersistentStateComponent) {
        XmlSerializer.deserializeAndLoadState((PersistentStateComponent)model, element);
      }
      else {
        //noinspection deprecation
        model.readExternal(element);
      }

      registerOnDispose(model);
      myExtensions.add(model);
    }
    myConfigurationAccessor = new RootConfigurationAccessor();
    myExtensionToStateDigest = null;
  }

  @Override
  public boolean isWritable() {
    return myWritable;
  }

  @NotNull
  RootConfigurationAccessor getConfigurationAccessor() {
    return myConfigurationAccessor;
  }

  //creates modifiable model
  RootModelImpl(@NotNull RootModelImpl rootModel,
                @NotNull ModuleRootManagerImpl moduleRootManager,
                final boolean writable,
                @NotNull RootConfigurationAccessor rootConfigurationAccessor,
                @NotNull VirtualFilePointerManager filePointerManager,
                @NotNull ProjectRootManagerImpl projectRootManager) {
    myFilePointerManager = filePointerManager;
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;

    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager);

    myWritable = writable;
    myConfigurationAccessor = rootConfigurationAccessor;

    for (ContentEntry contentEntry : rootModel.myContent) {
      if (contentEntry instanceof ClonableContentEntry) {
        ContentEntry cloned = ((ClonableContentEntry)contentEntry).cloneEntry(this);
        myContent.add(cloned);
      }
    }

    setOrderEntriesFrom(rootModel);

    myExtensionToStateDigest = writable ? new THashMap<>() : null;

    for (ModuleExtension extension : rootModel.myExtensions) {
      ModuleExtension model = extension.getModifiableModel(writable);
      registerOnDispose(model);
      myExtensions.add(model);

      if (myExtensionToStateDigest != null && !(extension instanceof PersistentStateComponentWithModificationTracker)) {
        Element state = new Element("state");
        try {
          //noinspection deprecation
          extension.writeExternal(state);
          myExtensionToStateDigest.put(extension, Scheme_implKt.digest(state));
        }
        catch (Exception e) {
          LOG.warn(e);
        }
      }
    }
  }

  private void setOrderEntriesFrom(@NotNull RootModelImpl rootModel) {
    removeAllOrderEntries();
    for (OrderEntry orderEntry : rootModel.myOrderEntries) {
      if (orderEntry instanceof ClonableOrderEntry) {
        myOrderEntries.add(((ClonableOrderEntry)orderEntry).cloneEntry(this, myProjectRootManager, myFilePointerManager));
      }
    }
  }

  private void removeAllOrderEntries() {
    for (OrderEntry entry : myOrderEntries) {
      Disposer.dispose((OrderEntryBaseImpl)entry);
    }
    myOrderEntries.clear();
  }

  @Override
  @NotNull
  public OrderEntry[] getOrderEntries() {
    OrderEntry[] cachedOrderEntries = myCachedOrderEntries;
    if (cachedOrderEntries == null) {
      myCachedOrderEntries = cachedOrderEntries = myOrderEntries.toArray(new OrderEntry[myOrderEntries.size()]);
    }
    return cachedOrderEntries;
  }

  @NotNull
  Iterator<OrderEntry> getOrderIterator() {
    return Collections.unmodifiableList(myOrderEntries).iterator();
  }

  @Override
  public void removeContentEntry(@NotNull ContentEntry entry) {
    assertWritable();
    LOG.assertTrue(myContent.contains(entry));
    if (entry instanceof RootModelComponentBase) {
      Disposer.dispose((RootModelComponentBase)entry);
      RootModelImpl entryModel = ((RootModelComponentBase)entry).getRootModel();
      LOG.assertTrue(entryModel == this, "Removing from " + this + " content entry obtained from " + entryModel);
    }
    myContent.remove(entry);
  }

  @Override
  public void addOrderEntry(@NotNull OrderEntry entry) {
    assertWritable();
    LOG.assertTrue(!myOrderEntries.contains(entry));
    myOrderEntries.add(entry);
  }

  @NotNull
  @Override
  public LibraryOrderEntry addLibraryEntry(@NotNull Library library) {
    assertWritable();
    final LibraryOrderEntry libraryOrderEntry = new LibraryOrderEntryImpl(library, this, myProjectRootManager);
    if (!libraryOrderEntry.isValid()) {
      LibraryEx libraryEx = ObjectUtils.tryCast(library, LibraryEx.class);
      boolean libraryDisposed = libraryEx != null ? libraryEx.isDisposed() : Disposer.isDisposed(library);
      throw new AssertionError("Invalid libraryOrderEntry, library: " + library
                               + " of type " + library.getClass()
                               + ", disposed: " + libraryDisposed
                               + ", kind: " + (libraryEx != null ? libraryEx.getKind() : "<undefined>"));
    }
    myOrderEntries.add(libraryOrderEntry);
    return libraryOrderEntry;
  }

  @NotNull
  @Override
  public LibraryOrderEntry addInvalidLibrary(@NotNull String name, @NotNull String level) {
    assertWritable();
    final LibraryOrderEntry libraryOrderEntry = new LibraryOrderEntryImpl(name, level, this, myProjectRootManager);
    myOrderEntries.add(libraryOrderEntry);
    return libraryOrderEntry;
  }

  @NotNull
  @Override
  public ModuleOrderEntry addModuleOrderEntry(@NotNull Module module) {
    assertWritable();
    LOG.assertTrue(!module.equals(getModule()));
    LOG.assertTrue(Comparing.equal(myModuleRootManager.getModule().getProject(), module.getProject()));
    final ModuleOrderEntryImpl moduleOrderEntry = new ModuleOrderEntryImpl(module, this);
    myOrderEntries.add(moduleOrderEntry);
    return moduleOrderEntry;
  }

  @NotNull
  @Override
  public ModuleOrderEntry addInvalidModuleEntry(@NotNull String name) {
    assertWritable();
    LOG.assertTrue(!name.equals(getModule().getName()));
    final ModuleOrderEntryImpl moduleOrderEntry = new ModuleOrderEntryImpl(name, this);
    myOrderEntries.add(moduleOrderEntry);
    return moduleOrderEntry;
  }

  @Nullable
  @Override
  public ModuleOrderEntry findModuleOrderEntry(@NotNull Module module) {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry && module.equals(((ModuleOrderEntry)orderEntry).getModule())) {
        return (ModuleOrderEntry)orderEntry;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public LibraryOrderEntry findLibraryOrderEntry(@NotNull Library library) {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry && library.equals(((LibraryOrderEntry)orderEntry).getLibrary())) {
        return (LibraryOrderEntry)orderEntry;
      }
    }
    return null;
  }

  @Override
  public void removeOrderEntry(@NotNull OrderEntry entry) {
    assertWritable();
    removeOrderEntryInternal(entry);
  }

  private void removeOrderEntryInternal(@NotNull OrderEntry entry) {
    LOG.assertTrue(myOrderEntries.contains(entry));
    Disposer.dispose((OrderEntryBaseImpl)entry);
    myOrderEntries.remove(entry);
  }

  @Override
  public void rearrangeOrderEntries(@NotNull OrderEntry[] newEntries) {
    assertWritable();
    assertValidRearrangement(newEntries);
    myOrderEntries.clear();
    ContainerUtil.addAll(myOrderEntries, newEntries);
  }

  private void assertValidRearrangement(@NotNull OrderEntry[] newEntries) {
    String error = checkValidRearrangement(newEntries);
    LOG.assertTrue(error == null, error);
  }

  @Nullable
  private String checkValidRearrangement(@NotNull OrderEntry[] newEntries) {
    if (newEntries.length != myOrderEntries.size()) {
      return "Size mismatch: old size=" + myOrderEntries.size() + "; new size=" + newEntries.length;
    }
    Set<OrderEntry> set = new HashSet<>();
    for (OrderEntry newEntry : newEntries) {
      if (!myOrderEntries.contains(newEntry)) {
        return "Trying to add nonexisting order entry " + newEntry;
      }

      if (set.contains(newEntry)) {
        return "Trying to add duplicate order entry " + newEntry;
      }
      set.add(newEntry);
    }
    return null;
  }

  @Override
  public void clear() {
    final Sdk jdk = getSdk();
    removeAllContentEntries();
    removeAllOrderEntries();
    setSdk(jdk);
    addSourceOrderEntries();
  }

  private void removeAllContentEntries() {
    for (ContentEntry entry : myContent) {
      if (entry instanceof RootModelComponentBase) {
        Disposer.dispose((RootModelComponentBase)entry);
      }
    }
    myContent.clear();
  }

  @Override
  public void commit() {
    myModuleRootManager.commitModel(this);
    myWritable = false;
  }

  void docommit() {
    assert isWritable();

    if (areOrderEntriesChanged()) {
      getSourceModel().setOrderEntriesFrom(this);
    }

    for (ModuleExtension extension : myExtensions) {
      if (extension.isChanged()) {
        extension.commit();
      }
    }

    if (areContentEntriesChanged()) {
      getSourceModel().removeAllContentEntries();
      for (ContentEntry contentEntry : myContent) {
        ContentEntry cloned = ((ClonableContentEntry)contentEntry).cloneEntry(getSourceModel());
        getSourceModel().myContent.add(cloned);
      }
    }
  }

  @Override
  @NotNull
  public LibraryTable getModuleLibraryTable() {
    return myModuleLibraryTable;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProjectRootManager.getProject();
  }

  @Override
  @NotNull
  public ContentEntry addContentEntry(@NotNull VirtualFile file) {
    return addContentEntry(new ContentEntryImpl(file, this));
  }

  @Override
  @NotNull
  public ContentEntry addContentEntry(@NotNull String url) {
    return addContentEntry(new ContentEntryImpl(url, this));
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @NotNull
  private ContentEntry addContentEntry(@NotNull ContentEntry e) {
    if (myContent.contains(e)) {
      for (ContentEntry contentEntry : getContentEntries()) {
        if (ContentComparator.INSTANCE.compare(contentEntry, e) == 0) return contentEntry;
      }
    }
    myContent.add(e);
    return e;
  }

  long getStateModificationCount() {
    long result = 0;
    for (ModuleExtension extension : myExtensions) {
      if (extension instanceof PersistentStateComponentWithModificationTracker) {
        result += ((PersistentStateComponentWithModificationTracker)extension).getStateModificationCount();
      }
    }
    return result;
  }

  public void writeExternal(@NotNull Element element) {
    for (ModuleExtension extension : myExtensions) {
      if (extension instanceof PersistentStateComponent) {
        //noinspection ConstantConditions
        XmlSerializer.serializeStateInto(((PersistentStateComponent)extension), element);
      }
      else {
        //noinspection deprecation
        extension.writeExternal(element);
      }
    }

    for (ContentEntry contentEntry : getContent()) {
      if (contentEntry instanceof ContentEntryImpl) {
        final Element subElement = new Element(ContentEntryImpl.ELEMENT_NAME);
        ((ContentEntryImpl)contentEntry).writeExternal(subElement);
        element.addContent(subElement);
      }
    }

    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof WritableOrderEntry) {
        ((WritableOrderEntry)orderEntry).writeExternal(element);
      }
    }
  }

  @Override
  public void setSdk(@Nullable Sdk jdk) {
    assertWritable();
    JdkOrderEntry jdkLibraryEntry = jdk == null ? null : new ModuleJdkOrderEntryImpl(jdk, this, myProjectRootManager);
    replaceEntryOfType(JdkOrderEntry.class, jdkLibraryEntry);
  }

  @Override
  public void setInvalidSdk(@NotNull String jdkName, String jdkType) {
    assertWritable();
    replaceEntryOfType(JdkOrderEntry.class, new ModuleJdkOrderEntryImpl(jdkName, jdkType, this, myProjectRootManager));
  }

  @Override
  public void inheritSdk() {
    assertWritable();
    replaceEntryOfType(JdkOrderEntry.class, new InheritedJdkOrderEntryImpl(this, myProjectRootManager));
  }


  @Override
  public <T extends OrderEntry> void replaceEntryOfType(@NotNull Class<T> entryClass, @Nullable final T entry) {
    assertWritable();
    for (int i = 0; i < myOrderEntries.size(); i++) {
      OrderEntry orderEntry = myOrderEntries.get(i);
      if (entryClass.isInstance(orderEntry)) {
        myOrderEntries.remove(i);
        if (entry != null) {
          myOrderEntries.add(i, entry);
        }
        return;
      }
    }

    if (entry != null) {
      myOrderEntries.add(0, entry);
    }
  }

  @Override
  public String getSdkName() {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof JdkOrderEntry) {
        return ((JdkOrderEntry)orderEntry).getJdkName();
      }
    }
    return null;
  }

  void assertWritable() {
    LOG.assertTrue(myWritable);
  }

  boolean isOrderEntryDisposed() {
    for (OrderEntry entry : myOrderEntries) {
      if (entry instanceof RootModelComponentBase && ((RootModelComponentBase)entry).isDisposed()) return true;
    }
    return false;
  }

  @Override
  protected Set<ContentEntry> getContent() {
    return myContent;
  }

  private static class ContentComparator implements Comparator<ContentEntry> {
    public static final ContentComparator INSTANCE = new ContentComparator();

    @Override
    public int compare(@NotNull final ContentEntry o1, @NotNull final ContentEntry o2) {
      return o1.getUrl().compareTo(o2.getUrl());
    }
  }

  @Override
  @NotNull
  public Module getModule() {
    return myModuleRootManager.getModule();
  }

  @Override
  public boolean isChanged() {
    if (!myWritable) return false;

    for (ModuleExtension moduleExtension : myExtensions) {
      if (moduleExtension.isChanged()) return true;
    }

    return areOrderEntriesChanged() || areContentEntriesChanged();
  }

  private boolean areContentEntriesChanged() {
    return ArrayUtil.lexicographicCompare(getContentEntries(), getSourceModel().getContentEntries()) != 0;
  }

  private boolean areOrderEntriesChanged() {
    OrderEntry[] orderEntries = getOrderEntries();
    OrderEntry[] sourceOrderEntries = getSourceModel().getOrderEntries();
    if (orderEntries.length != sourceOrderEntries.length) return true;
    for (int i = 0; i < orderEntries.length; i++) {
      OrderEntry orderEntry = orderEntries[i];
      OrderEntry sourceOrderEntry = sourceOrderEntries[i];
      if (!orderEntriesEquals(orderEntry, sourceOrderEntry)) {
        return true;
      }
    }
    return false;
  }

  private static boolean orderEntriesEquals(@NotNull OrderEntry orderEntry1, @NotNull OrderEntry orderEntry2) {
    if (!((OrderEntryBaseImpl)orderEntry1).sameType(orderEntry2)) return false;
    if (orderEntry1 instanceof JdkOrderEntry) {
      if (!(orderEntry2 instanceof JdkOrderEntry)) return false;
      if (orderEntry1 instanceof InheritedJdkOrderEntry && orderEntry2 instanceof ModuleJdkOrderEntry) {
        return false;
      }
      if (orderEntry2 instanceof InheritedJdkOrderEntry && orderEntry1 instanceof ModuleJdkOrderEntry) {
        return false;
      }
      if (orderEntry1 instanceof ModuleJdkOrderEntry && orderEntry2 instanceof ModuleJdkOrderEntry) {
        String name1 = ((ModuleJdkOrderEntry)orderEntry1).getJdkName();
        String name2 = ((ModuleJdkOrderEntry)orderEntry2).getJdkName();
        if (!Comparing.strEqual(name1, name2)) {
          return false;
        }
      }
    }
    if (orderEntry1 instanceof ExportableOrderEntry) {
      if (((ExportableOrderEntry)orderEntry1).isExported() != ((ExportableOrderEntry)orderEntry2).isExported()) {
        return false;
      }
      if (((ExportableOrderEntry)orderEntry1).getScope() != ((ExportableOrderEntry)orderEntry2).getScope()) {
        return false;
      }
    }
    if (orderEntry1 instanceof ModuleOrderEntry) {
      LOG.assertTrue(orderEntry2 instanceof ModuleOrderEntry);
      ModuleOrderEntryImpl entry1 = (ModuleOrderEntryImpl)orderEntry1;
      ModuleOrderEntryImpl entry2 = (ModuleOrderEntryImpl)orderEntry2;
      return entry1.isProductionOnTestDependency() == entry2.isProductionOnTestDependency()
             && Comparing.equal(entry1.getModuleName(), entry2.getModuleName());
    }

    if (orderEntry1 instanceof LibraryOrderEntry) {
      LOG.assertTrue(orderEntry2 instanceof LibraryOrderEntry);
      LibraryOrderEntry libraryOrderEntry1 = (LibraryOrderEntry)orderEntry1;
      LibraryOrderEntry libraryOrderEntry2 = (LibraryOrderEntry)orderEntry2;
      boolean equal = Comparing.equal(libraryOrderEntry1.getLibraryName(), libraryOrderEntry2.getLibraryName())
                      && Comparing.equal(libraryOrderEntry1.getLibraryLevel(), libraryOrderEntry2.getLibraryLevel());
      if (!equal) return false;

      Library library1 = libraryOrderEntry1.getLibrary();
      Library library2 = libraryOrderEntry2.getLibrary();
      if (library1 != null && library2 != null) {
        if (!Arrays.equals(((LibraryEx)library1).getExcludedRootUrls(), ((LibraryEx)library2).getExcludedRootUrls())) {
          return false;
        }
      }
    }

    final OrderRootType[] allTypes = OrderRootType.getAllTypes();
    for (OrderRootType type : allTypes) {
      final String[] orderedRootUrls1 = orderEntry1.getUrls(type);
      final String[] orderedRootUrls2 = orderEntry2.getUrls(type);
      if (!Arrays.equals(orderedRootUrls1, orderedRootUrls2)) {
        return false;
      }
    }
    return true;
  }

  void makeExternalChange(@NotNull Runnable runnable) {
    if (myWritable || myDisposed) return;
    myModuleRootManager.makeRootsChange(runnable);
  }

  @Override
  public void dispose() {
    assert !myDisposed;
    Disposer.dispose(myDisposable);
    myExtensions.clear();

    if (myExtensionToStateDigest != null) {
      myExtensionToStateDigest.clear();

    }

    myWritable = false;
    myDisposed = true;
  }

  private class Order extends ArrayList<OrderEntry> {
    @Override
    public void clear() {
      super.clear();
      clearCachedEntries();
    }

    @NotNull
    @Override
    public OrderEntry set(int i, @NotNull OrderEntry orderEntry) {
      super.set(i, orderEntry);
      ((OrderEntryBaseImpl)orderEntry).setIndex(i);
      clearCachedEntries();
      return orderEntry;
    }

    @Override
    public boolean add(@NotNull OrderEntry orderEntry) {
      super.add(orderEntry);
      ((OrderEntryBaseImpl)orderEntry).setIndex(size() - 1);
      clearCachedEntries();
      return true;
    }

    @Override
    public void add(int i, OrderEntry orderEntry) {
      super.add(i, orderEntry);
      clearCachedEntries();
      setIndicies(i);
    }

    @Override
    public OrderEntry remove(int i) {
      OrderEntry entry = super.remove(i);
      setIndicies(i);
      clearCachedEntries();
      return entry;
    }

    @Override
    public boolean remove(Object o) {
      int index = indexOf(o);
      if (index < 0) return false;
      remove(index);
      clearCachedEntries();
      return true;
    }

    @Override
    public boolean addAll(Collection<? extends OrderEntry> collection) {
      int startSize = size();
      boolean result = super.addAll(collection);
      setIndicies(startSize);
      clearCachedEntries();
      return result;
    }

    @Override
    public boolean addAll(int i, Collection<? extends OrderEntry> collection) {
      boolean result = super.addAll(i, collection);
      setIndicies(i);
      clearCachedEntries();
      return result;
    }

    @Override
    public void removeRange(int i, int i1) {
      super.removeRange(i, i1);
      clearCachedEntries();
      setIndicies(i);
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
      boolean result = super.removeAll(collection);
      setIndicies(0);
      clearCachedEntries();
      return result;
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
      boolean result = super.retainAll(collection);
      setIndicies(0);
      clearCachedEntries();
      return result;
    }

    private void clearCachedEntries() {
      myCachedOrderEntries = null;
    }

    private void setIndicies(int startIndex) {
      for (int j = startIndex; j < size(); j++) {
        ((OrderEntryBaseImpl)get(j)).setIndex(j);
      }
    }
  }

  private RootModelImpl getSourceModel() {
    assertWritable();
    return myModuleRootManager.getRootModel();
  }

  @Override
  public String toString() {
    return "RootModelImpl{" +
           "module=" + getModule().getName() +
           ", writable=" + myWritable +
           ", disposed=" + myDisposed +
           '}';
  }

  @Nullable
  @Override
  public <T> T getModuleExtension(@NotNull final Class<T> klass) {
    for (ModuleExtension extension : myExtensions) {
      if (klass.isAssignableFrom(extension.getClass())) {
        //noinspection unchecked
        return (T)extension;
      }
    }
    return null;
  }

  void registerOnDispose(@NotNull Disposable disposable) {
    myDisposable.add(disposable);
  }

  void checkModuleExtensionModification() {
    if (myExtensionToStateDigest == null || myExtensionToStateDigest.isEmpty()) {
      return;
    }

    for (Map.Entry<ModuleExtension, byte[]> entry : myExtensionToStateDigest.entrySet()) {
      Element state = new Element("state");
      try {
        ModuleExtension extension = entry.getKey();
        //noinspection deprecation
        extension.writeExternal(state);
        byte[] newDigest = Scheme_implKt.digest(state);
        if (!Arrays.equals(newDigest, entry.getValue())) {
          myModuleRootManager.stateChanged();
          return;
        }
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
  }
}
