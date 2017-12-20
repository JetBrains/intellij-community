// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.configurationStore.ComponentSerializationUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StateSplitterEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtilCore;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.VirtualFilePointerContainerImpl;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public class LibraryImpl extends TraceableDisposable implements LibraryEx.ModifiableModelEx, LibraryEx, RootProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.impl.LibraryImpl");
  @NonNls public static final String LIBRARY_NAME_ATTR = "name";
  @NonNls private static final String LIBRARY_TYPE_ATTR = "type";
  @NonNls private static final String ROOT_PATH_ELEMENT = "root";
  @NonNls public static final String ELEMENT = "library";
  @NonNls private static final String PROPERTIES_ELEMENT = "properties";
  private static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();
  private static final String EXCLUDED_ROOTS_TAG = "excluded";
  private String myName;
  private final LibraryTable myLibraryTable;
  private final Map<OrderRootType, VirtualFilePointerContainer> myRoots;
  @Nullable private VirtualFilePointerContainer myExcludedRoots;
  private final LibraryImpl mySource;
  private PersistentLibraryKind<?> myKind;
  private LibraryProperties myProperties;

  @Nullable
  private final ModifiableRootModel myRootModel;
  private boolean myDisposed;
  private final Disposable myPointersDisposable = Disposer.newDisposable();
  private final ProjectModelExternalSource myExternalSource;
  private final EventDispatcher<RootSetChangedListener> myDispatcher = EventDispatcher.create(RootSetChangedListener.class);

  LibraryImpl(LibraryTable table, @NotNull Element element, ModifiableRootModel rootModel) throws InvalidDataException {
    this(table, rootModel, null, null, findPersistentLibraryKind(element), findExternalSource(element));
    readExternal(element);
  }

  LibraryImpl(String name, @Nullable final PersistentLibraryKind<?> kind, LibraryTable table, ModifiableRootModel rootModel,
              ProjectModelExternalSource externalSource) {
    this(table, rootModel, null, name, kind, externalSource);
    if (kind != null) {
      myProperties = kind.createDefaultProperties();
    }
  }

  private LibraryImpl(@NotNull LibraryImpl from, LibraryImpl newSource, ModifiableRootModel rootModel) {
    this(from.myLibraryTable, rootModel, newSource, from.myName, from.myKind, from.myExternalSource);
    from.checkDisposed();

    if (from.myKind != null && from.myProperties != null) {
      myProperties = myKind.createDefaultProperties();
      //noinspection unchecked
      myProperties.loadState(from.myProperties.getState());
    }
    for (OrderRootType rootType : getAllRootTypes()) {
      final VirtualFilePointerContainer thisContainer = myRoots.get(rootType);
      final VirtualFilePointerContainer thatContainer = from.myRoots.get(rootType);
      thisContainer.addAll(thatContainer);
    }
    if (from.myExcludedRoots != null) {
      myExcludedRoots = from.myExcludedRoots.clone(myPointersDisposable);
    }
  }

  // primary
  private LibraryImpl(LibraryTable table, @Nullable ModifiableRootModel rootModel, LibraryImpl newSource, String name,
                      @Nullable PersistentLibraryKind<?> kind, @Nullable ProjectModelExternalSource externalSource) {
    super(true);
    myLibraryTable = table;
    myRootModel = rootModel;
    mySource = newSource;
    myKind = kind;
    myName = name;
    myExternalSource = externalSource;
    //init roots depends on my myKind
    myRoots = initRoots();
  }

  @Nullable
  private static ProjectModelExternalSource findExternalSource(Element element) {
    @Nullable String externalSourceId = element.getAttributeValue(StateSplitterEx.EXTERNAL_SYSTEM_ID_ATTRIBUTE);
    return externalSourceId != null ? ExternalProjectSystemRegistry.getInstance().getSourceById(externalSourceId) : null;
  }

  @Nullable
  private static PersistentLibraryKind<?> findPersistentLibraryKind(@NotNull Element element) {
    String typeString = element.getAttributeValue(LIBRARY_TYPE_ATTR);
    LibraryKind kind = LibraryKind.findById(typeString);
    if (kind != null && !(kind instanceof PersistentLibraryKind<?>)) {
      LOG.error("Cannot load non-persistable library kind: " + typeString);
      return null;
    }
    return (PersistentLibraryKind<?>)kind;
  }

  @NotNull
  private Set<OrderRootType> getAllRootTypes() {
    Set<OrderRootType> rootTypes = new HashSet<>(Arrays.asList(OrderRootType.getAllTypes()));
    if (myKind != null) {
      rootTypes.addAll(Arrays.asList(myKind.getAdditionalRootTypes()));
    }
    return rootTypes;
  }

  @Override
  public void dispose() {
    checkDisposed();

    myDisposed = true;
    kill(null);
  }

  private void checkDisposed() {
    if (isDisposed()) {
      throwDisposalError("'" + myName + "' already disposed: " + getStackTrace());
    }
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String[] getUrls(@NotNull OrderRootType rootType) {
    checkDisposed();

    VirtualFilePointerContainer result = myRoots.get(rootType);
    return result == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : result.getUrls();
  }

  @Override
  @NotNull
  public VirtualFile[] getFiles(@NotNull OrderRootType rootType) {
    checkDisposed();

    VirtualFilePointerContainer container = myRoots.get(rootType);
    return container == null ? VirtualFile.EMPTY_ARRAY : container.getFiles();
  }

  @Override
  public void setName(String name) {
    LOG.assertTrue(isWritable());
    myName = name;
  }

  /* you have to commit modifiable model or dispose it by yourself! */
  @Override
  @NotNull
  public ModifiableModelEx getModifiableModel() {
    checkDisposed();
    return new LibraryImpl(this, this, myRootModel);
  }

  @NotNull
  public Library cloneLibrary(@NotNull RootModelImpl rootModel) {
    LOG.assertTrue(myLibraryTable == null);
    return new LibraryImpl(this, null, rootModel);
  }

  @NotNull
  @Override
  public List<String> getInvalidRootUrls(@NotNull OrderRootType type) {
    if (myDisposed) return Collections.emptyList();

    final List<VirtualFilePointer> pointers = myRoots.get(type).getList();
    List<String> invalidPaths = null;
    for (VirtualFilePointer pointer : pointers) {
      if (!pointer.isValid()) {
        if (invalidPaths == null) {
          invalidPaths = new SmartList<>();
        }
        invalidPaths.add(pointer.getUrl());
      }
    }
    return ContainerUtil.notNullize(invalidPaths);
  }

  @Override
  public void setProperties(LibraryProperties properties) {
    LOG.assertTrue(isWritable());
    myProperties = properties;
  }

  @Override
  @NotNull
  public RootProvider getRootProvider() {
    return this;
  }

  @NotNull
  private Map<OrderRootType, VirtualFilePointerContainer> initRoots() {
    Disposer.register(this, myPointersDisposable);

    Map<OrderRootType, VirtualFilePointerContainer> result = new HashMap<>(4);

    VirtualFilePointerListener listener = getListener();

    for (OrderRootType rootType : getAllRootTypes()) {
      VirtualFilePointerContainer container = VirtualFilePointerManager.getInstance().createContainer(myPointersDisposable, listener);
      result.put(rootType, container);
    }

    return result;
  }

  @NotNull
  private VirtualFilePointerListener getListener() {
    Project project = myLibraryTable instanceof ProjectLibraryTable ? ((ProjectLibraryTable)myLibraryTable).getProject() : null;
    return myRootModel != null ? ((RootModelImpl)myRootModel).getRootsChangedListener() : project != null ? ProjectRootManagerImpl
      .getInstanceImpl(project).getRootsValidityChangedListener() : ProjectJdkImpl.getGlobalVirtualFilePointerListener();
  }

  @Nullable
  @Override
  public ProjectModelExternalSource getExternalSource() {
    return myExternalSource;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    readName(element);
    readProperties(element);
    readRoots(element);
    readJarDirectories(element);
  }

  @NonNls private static final String ROOT_TYPE_ATTR = "type";
  private static final OrderRootType DEFAULT_JAR_DIRECTORY_TYPE = OrderRootType.CLASSES;

  // just to maintain .xml compatibility
  // VirtualFilePointerContainerImpl does the same but stores its jar dirs attributes inside <root> element
  @Deprecated // todo to remove sometime later
  private void readJarDirectories(Element element) {
    final List<Element> jarDirs = element.getChildren(VirtualFilePointerContainerImpl.JAR_DIRECTORY_ELEMENT);
    for (Element jarDir : jarDirs) {
      final String url = jarDir.getAttributeValue(VirtualFilePointerContainerImpl.URL_ATTR);
      if (url != null) {
        final String recursive = jarDir.getAttributeValue(VirtualFilePointerContainerImpl.RECURSIVE_ATTR);
        final OrderRootType rootType = getJarDirectoryRootType(jarDir.getAttributeValue(ROOT_TYPE_ATTR));
        VirtualFilePointerContainer roots = myRoots.get(rootType);
        boolean recursively = Boolean.parseBoolean(recursive);
        roots.addJarDirectory(url, recursively);
      }
    }
  }

  private static OrderRootType getJarDirectoryRootType(@Nullable String type) {
    for (PersistentOrderRootType rootType : OrderRootType.getAllPersistentTypes()) {
      if (rootType.name().equals(type)) {
        return rootType;
      }
    }
    return DEFAULT_JAR_DIRECTORY_TYPE;
  }

  private void readProperties(Element element) {
    final String typeId = element.getAttributeValue(LIBRARY_TYPE_ATTR);
    if (typeId == null) return;

    myKind = (PersistentLibraryKind<?>) LibraryKind.findById(typeId);
    if (myKind == null) return;

    myProperties = myKind.createDefaultProperties();
    final Element propertiesElement = element.getChild(PROPERTIES_ELEMENT);
    if (propertiesElement != null) {
      ComponentSerializationUtil.loadComponentState(myProperties, propertiesElement);
    }
  }

  private void readName(@NotNull Element element) {
    myName = element.getAttributeValue(LIBRARY_NAME_ATTR);
  }

  private void readRoots(@NotNull Element element) throws InvalidDataException {
    for (OrderRootType rootType : getAllRootTypes()) {
      final Element rootChild = element.getChild(rootType.name());
      if (rootChild == null) {
        continue;
      }
      VirtualFilePointerContainer roots = myRoots.get(rootType);
      roots.readExternal(rootChild, ROOT_PATH_ELEMENT, false);
    }
    Element excludedRoot = element.getChild(EXCLUDED_ROOTS_TAG);
    if (excludedRoot != null) {
      getOrCreateExcludedRoots().readExternal(excludedRoot, ROOT_PATH_ELEMENT, false);
    }
  }

  @NotNull
  private VirtualFilePointerContainer getOrCreateExcludedRoots() {
    if (myExcludedRoots == null) {
      myExcludedRoots = VirtualFilePointerManager.getInstance().createContainer(myPointersDisposable);
    }
    return myExcludedRoots;
  }

  //TODO<rv> Remove the next two methods as a temporary solution. Sort in OrderRootType.
  //
  @NotNull
  private static List<OrderRootType> sortRootTypes(@NotNull Collection<OrderRootType> rootTypes) {
    List<OrderRootType> allTypes = new ArrayList<>(rootTypes);
    Collections.sort(allTypes, (o1, o2) -> o1.name().compareToIgnoreCase(o2.name()));
    return allTypes;
  }

  @Override
  public void writeExternal(Element rootElement) {
    checkDisposed();

    Element element = new Element(ELEMENT);
    if (myName != null) {
      element.setAttribute(LIBRARY_NAME_ATTR, myName);
    }
    if (myKind != null) {
      element.setAttribute(LIBRARY_TYPE_ATTR, myKind.getKindId());
      LOG.assertTrue(myProperties != null, "Properties is 'null' in library with kind " + myKind);
      final Object state = myProperties.getState();
      if (state != null) {
        final Element propertiesElement = XmlSerializer.serializeIfNotDefault(state, SERIALIZATION_FILTERS);
        if (!JDOMUtil.isEmpty(propertiesElement)) {
          element.addContent(propertiesElement.setName(PROPERTIES_ELEMENT));
        }
      }
    }

    if (myExternalSource != null) {
      Module module = getModule();
      Project project;
      if (module == null) {
        project = myLibraryTable instanceof ProjectLibraryTable ? ((ProjectLibraryTable)myLibraryTable).getProject() : null;
      }
      else {
        project = module.getProject();
      }
      if (ProjectUtilCore.isExternalStorageEnabled(project)) {
        //we can add this attribute only if the library configuration will be stored separately, otherwise we will get modified files in .idea/libraries.
        element.setAttribute(StateSplitterEx.EXTERNAL_SYSTEM_ID_ATTRIBUTE, myExternalSource.getId());
      }
    }

    List<OrderRootType> storableRootTypes = new ArrayList<>(Arrays.asList(OrderRootType.getAllTypes()));
    if (myKind != null) {
      storableRootTypes.addAll(Arrays.asList(myKind.getAdditionalRootTypes()));
    }
    for (OrderRootType rootType : sortRootTypes(storableRootTypes)) {
      final VirtualFilePointerContainer roots = myRoots.get(rootType);
      if (roots.size() == 0 && rootType.skipWriteIfEmpty()) {
        //compatibility iml/ipr
        continue;
      }

      final Element rootTypeElement = new Element(rootType.name());
      roots.writeExternal(rootTypeElement, ROOT_PATH_ELEMENT, false);
      element.addContent(rootTypeElement);
    }
    if (myExcludedRoots != null && myExcludedRoots.size() > 0) {
      Element excluded = new Element(EXCLUDED_ROOTS_TAG);
      myExcludedRoots.writeExternal(excluded, ROOT_PATH_ELEMENT, false);
      element.addContent(excluded);
    }
    writeJarDirectories(element);
    rootElement.addContent(element);
  }

  // just to maintain .xml compatibility
  // VirtualFilePointerContainerImpl does the same but stores its jar dirs attributes inside <root> element
  @Deprecated // todo to remove sometime later
  private void writeJarDirectories(Element element) {
    final List<OrderRootType> rootTypes = sortRootTypes(myRoots.keySet());
    for (OrderRootType rootType : rootTypes) {
      VirtualFilePointerContainer container = myRoots.get(rootType);
      List<Pair<String, Boolean>> jarDirectories = new ArrayList<>(container.getJarDirectories());
      Collections.sort(jarDirectories, Comparator.comparing(p->p.getFirst(), String.CASE_INSENSITIVE_ORDER));
      for (Pair<String, Boolean> pair : jarDirectories) {
        String url = pair.getFirst();
        boolean isRecursive = pair.getSecond();
        final Element jarDirElement = new Element(VirtualFilePointerContainerImpl.JAR_DIRECTORY_ELEMENT);
        jarDirElement.setAttribute(VirtualFilePointerContainerImpl.URL_ATTR, url);
        jarDirElement.setAttribute(VirtualFilePointerContainerImpl.RECURSIVE_ATTR, Boolean.toString(isRecursive));
        if (!rootType.equals(DEFAULT_JAR_DIRECTORY_TYPE)) {
          jarDirElement.setAttribute(ROOT_TYPE_ATTR, rootType.name());
        }
        element.addContent(jarDirElement);
      }
    }
  }

  private boolean isWritable() {
    return mySource != null;
  }

  @Nullable
  @Override
  public PersistentLibraryKind<?> getKind() {
    return myKind;
  }

  @Override
  public void addExcludedRoot(@NotNull String url) {
    VirtualFilePointerContainer roots = getOrCreateExcludedRoots();
    if (roots.findByUrl(url) == null) {
      roots.add(url);
    }
  }

  @Override
  public boolean removeExcludedRoot(@NotNull String url) {
    if (myExcludedRoots != null) {
      VirtualFilePointer pointer = myExcludedRoots.findByUrl(url);
      if (pointer != null) {
        myExcludedRoots.remove(pointer);
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public String[] getExcludedRootUrls() {
    return myExcludedRoots != null ? myExcludedRoots.getUrls() : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  @Override
  public VirtualFile[] getExcludedRoots() {
    return myExcludedRoots != null ? myExcludedRoots.getFiles() : VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public LibraryProperties getProperties() {
    return myProperties;
  }

  @Override
  public void setKind(@NotNull PersistentLibraryKind<?> kind) {
    LOG.assertTrue(isWritable());
    LOG.assertTrue(myKind == null || myKind == kind, "Library kind cannot be changed from " + myKind + " to " + kind);
    myKind = kind;
    myProperties = kind.createDefaultProperties();
  }

  @Override
  public void addRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    checkDisposed();
    LOG.assertTrue(isWritable());

    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.add(url);
  }

  @Override
  public void addRoot(@NotNull VirtualFile file, @NotNull OrderRootType rootType) {
    checkDisposed();
    LOG.assertTrue(isWritable());

    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.add(file);
  }

  @Override
  public void addJarDirectory(@NotNull final String url, final boolean recursive) {
    addJarDirectory(url, recursive, OrderRootType.CLASSES);
  }

  @Override
  public void addJarDirectory(@NotNull final VirtualFile file, final boolean recursive) {
    addJarDirectory(file, recursive, OrderRootType.CLASSES);
  }

  @Override
  public void addJarDirectory(@NotNull final String url, final boolean recursive, @NotNull OrderRootType rootType) {
    checkDisposed();
    LOG.assertTrue(isWritable());

    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.addJarDirectory(url, recursive);
  }

  @Override
  public void addJarDirectory(@NotNull final VirtualFile file, final boolean recursive, @NotNull OrderRootType rootType) {
    checkDisposed();
    LOG.assertTrue(isWritable());

    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.addJarDirectory(file.getUrl(), recursive);
  }

  @Override
  public boolean isJarDirectory(@NotNull final String url) {
    return isJarDirectory(url, OrderRootType.CLASSES);
  }

  @Override
  public boolean isJarDirectory(@NotNull final String url, @NotNull final OrderRootType rootType) {
    VirtualFilePointerContainer container = myRoots.get(rootType);
    List<Pair<String, Boolean>> jarDirectories = container.getJarDirectories();
    return jarDirectories.contains(Pair.create(url, false)) || jarDirectories.contains(Pair.create(url, true));
  }

  @Override
  public boolean isValid(@NotNull final String url, @NotNull final OrderRootType rootType) {
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    final VirtualFilePointer fp = container.findByUrl(url);
    return fp != null && fp.isValid();
  }

  @Override
  public boolean removeRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    checkDisposed();
    LOG.assertTrue(isWritable());

    final VirtualFilePointerContainer container = myRoots.get(rootType);
    final VirtualFilePointer byUrl = container.findByUrl(url);
    if (byUrl != null) {
      container.remove(byUrl);
      if (myExcludedRoots != null) {
        for (String excludedRoot : myExcludedRoots.getUrls()) {
          if (!isUnderRoots(excludedRoot)) {
            VirtualFilePointer pointer = myExcludedRoots.findByUrl(excludedRoot);
            if (pointer != null) {
              myExcludedRoots.remove(pointer);
            }
          }
        }
      }
      container.removeJarDirectory(url);
      return true;
    }
    return false;
  }

  private boolean isUnderRoots(@NotNull String url) {
    for (VirtualFilePointerContainer container : myRoots.values()) {
      if (VfsUtilCore.isUnder(url, Arrays.asList(container.getUrls()))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void moveRootUp(@NotNull String url, @NotNull OrderRootType rootType) {
    checkDisposed();
    LOG.assertTrue(isWritable());

    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.moveUp(url);
  }

  @Override
  public void moveRootDown(@NotNull String url, @NotNull OrderRootType rootType) {
    checkDisposed();
    LOG.assertTrue(isWritable());

    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.moveDown(url);
  }

  @Override
  public boolean isChanged() {
    return !mySource.equals(this);
  }

  private boolean areRootsChanged(@NotNull LibraryImpl that) {
    return !that.equals(this);
  }

  public Library getSource() {
    return mySource;
  }

  @Override
  public void commit() {
    checkDisposed();

    if (isChanged()) {
      mySource.commit(this);
    }
    Disposer.dispose(this);
  }

  private void commit(@NotNull LibraryImpl fromModel) {
    if (myLibraryTable != null) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    }
    if (!Comparing.equal(fromModel.myName, myName)) {
      myName = fromModel.myName;
      if (myLibraryTable instanceof LibraryTableBase) {
        ((LibraryTableBase)myLibraryTable).fireLibraryRenamed(this);
      }
    }
    myKind = fromModel.getKind();
    myProperties = fromModel.myProperties;
    if (areRootsChanged(fromModel)) {
      disposeMyPointers();
      copyRootsFrom(fromModel);
      fireRootSetChanged();
    }
  }

  private void copyRootsFrom(@NotNull LibraryImpl fromModel) {
    Map<OrderRootType, VirtualFilePointerContainer> clonedRoots = ContainerUtil.newHashMap();
    for (Map.Entry<OrderRootType, VirtualFilePointerContainer> entry : fromModel.myRoots.entrySet()) {
      OrderRootType rootType = entry.getKey();
      VirtualFilePointerContainer container = entry.getValue();
      VirtualFilePointerContainer clone = container.clone(myPointersDisposable, getListener());
      clonedRoots.put(rootType, clone);
    }
    myRoots.clear();
    myRoots.putAll(clonedRoots);

    VirtualFilePointerContainer excludedRoots = fromModel.myExcludedRoots;
    myExcludedRoots = excludedRoots != null ? excludedRoots.clone(myPointersDisposable) : null;
  }

  private void disposeMyPointers() {
    for (VirtualFilePointerContainer container : new THashSet<>(myRoots.values())) {
      container.killAll();
    }
    if (myExcludedRoots != null) {
      myExcludedRoots.killAll();
    }
    Disposer.dispose(myPointersDisposable);
    Disposer.register(this, myPointersDisposable);
  }

  @Override
  public LibraryTable getTable() {
    return myLibraryTable;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final LibraryImpl library = (LibraryImpl)o;

    if (myName != null ? !myName.equals(library.myName) : library.myName != null) return false;
    if (myRoots != null ? !myRoots.equals(library.myRoots) : library.myRoots != null) return false;
    if (myKind != null ? !myKind.equals(library.myKind) : library.myKind != null) return false;
    if (myProperties != null ? !myProperties.equals(library.myProperties) : library.myProperties != null) return false;
    if (!Comparing.equal(myExcludedRoots, library.myExcludedRoots)) return false;

    return true;
  }

  public int hashCode() {
    int result = myName != null ? myName.hashCode() : 0;
    result = 31 * result + (myRoots != null ? myRoots.hashCode() : 0);
    return result;
  }

  @NonNls
  @Override
  public String toString() {
    return "Library: name:" + myName + "; roots:" + myRoots.values();
  }

  @Nullable("will return non-null value only for module level libraries")
  public Module getModule() {
    return myRootModel == null ? null : myRootModel.getModule();
  }

  @Override
  public void addRootSetChangedListener(@NotNull RootSetChangedListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeRootSetChangedListener(@NotNull RootSetChangedListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public void addRootSetChangedListener(@NotNull RootSetChangedListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  private void fireRootSetChanged() {
    myDispatcher.getMulticaster().rootSetChanged(this);
  }
}
