// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.impl.RootProviderBaseImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

@ApiStatus.Internal
public class LegacyProjectJdkDelegate extends UserDataHolderBase implements SdkBridge, SdkModificator, Disposable {
  private static final Logger LOG = Logger.getInstance(LegacyProjectJdkDelegate.class);
  private String myName;
  private String myVersionString;
  private boolean myVersionDefined;
  private String myHomePath = "";
  private final RootsAsVirtualFilePointers myRoots;
  private LegacyProjectJdkDelegate myOrigin;
  private SdkAdditionalData myAdditionalData;
  private SdkTypeId mySdkType;
  @NonNls public static final String ELEMENT_NAME = "name";
  @NonNls public static final String ATTRIBUTE_VALUE = "value";
  @NonNls public static final String ELEMENT_TYPE = "type";
  @NonNls private static final String ELEMENT_VERSION = "version";
  @NonNls private static final String ELEMENT_ROOTS = "roots";
  @NonNls private static final String ELEMENT_HOMEPATH = "homePath";
  @NonNls public static final String ELEMENT_ADDITIONAL = "additional";
  private final MyRootProvider myRootProvider = new MyRootProvider();

  LegacyProjectJdkDelegate(String name, SdkTypeId sdkType) {
    mySdkType = sdkType;
    myName = name;

    myRoots = new RootsAsVirtualFilePointers(true, tellAllProjectsTheirRootsAreGoingToChange, this);
    // register on VirtualFilePointerManager because we want our virtual pointers to be disposed before VFPM to avoid "pointer leaked" diagnostics fired
    Disposer.register((Disposable)VirtualFilePointerManager.getInstance(), this);
  }

  LegacyProjectJdkDelegate(String name, SdkTypeId sdkType, String homePath, String version) {
    this(name, sdkType);
    myHomePath = homePath;
    myVersionString = version;
  }

  private static final VirtualFilePointerListener tellAllProjectsTheirRootsAreGoingToChange = new VirtualFilePointerListener() {
    @Override
    public void beforeValidityChanged(VirtualFilePointer @NotNull [] pointers) {
      //todo check if this sdk is really used in the project
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        VirtualFilePointerListener listener = ((ProjectRootManagerImpl)ProjectRootManager.getInstance(project)).getRootsValidityChangedListener();
        listener.beforeValidityChanged(pointers);
      }
    }

    @Override
    public void validityChanged(VirtualFilePointer @NotNull [] pointers) {
      //todo check if this sdk is really used in the project
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        VirtualFilePointerListener listener = ((ProjectRootManagerImpl)ProjectRootManager.getInstance(project)).getRootsValidityChangedListener();
        listener.validityChanged(pointers);
      }
    }
  };

  @NotNull
  public static VirtualFilePointerListener getGlobalVirtualFilePointerListener() {
    return tellAllProjectsTheirRootsAreGoingToChange;
  }

  @Override
  public void dispose() {
  }

  @Override
  @NotNull
  public SdkTypeId getSdkType() {
    SdkTypeId sdkType = mySdkType;
    if (sdkType == null) {
      mySdkType = sdkType = ProjectJdkTable.getInstance().getDefaultSdkType();
    }
    return sdkType;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  public final void setVersionString(@Nullable String versionString) {
    myVersionString = versionString == null || versionString.isEmpty() ? null : versionString;
    myVersionDefined = true;
  }

  @Override
  public String getVersionString() {
    if (myVersionString == null && !myVersionDefined) {
      String homePath = getHomePath();
      if (homePath != null && !homePath.isEmpty()) {
        setVersionString(getSdkType().getVersionString(this));
      }
    }
    return myVersionString;
  }

  public final void resetVersionString() {
    myVersionDefined = false;
    myVersionString = null;
  }

  @Override
  public String getHomePath() {
    return myHomePath;
  }

  @Override
  public VirtualFile getHomeDirectory() {
    if (myHomePath == null) {
      return null;
    }
    return StandardFileSystems.local().findFileByPath(myHomePath);
  }

  @Override
  public void readExternal(@NotNull Element element) {
    readExternal(element, ProjectJdkTable.getInstance()::getSdkTypeByName);
  }

  public void readExternal(@NotNull Element element, @NotNull Function<String, SdkTypeId> sdkTypeByNameFunction) throws
                                                                                                                 InvalidDataException {
    Element elementName = assertNotMissing(element, ELEMENT_NAME);
    myName = elementName.getAttributeValue(ATTRIBUTE_VALUE);
    final Element typeChild = element.getChild(ELEMENT_TYPE);
    final String sdkTypeName = typeChild != null ? typeChild.getAttributeValue(ATTRIBUTE_VALUE) : null;
    if (sdkTypeName != null) {
      mySdkType = sdkTypeByNameFunction.apply(sdkTypeName);
    }
    final Element version = element.getChild(ELEMENT_VERSION);

    // set version if it was cached (defined)
    // otherwise it will be null && undefined
    if (version != null) {
      setVersionString(version.getAttributeValue(ATTRIBUTE_VALUE));
    }
    else {
      myVersionDefined = false;
    }

    String versionValue = element.getAttributeValue(ELEMENT_VERSION, "");
    if (!"2".equals(versionValue)) {
      throw new InvalidDataException("Too old version is not supported: " + versionValue);
    }
    Element homePath = assertNotMissing(element, ELEMENT_HOMEPATH);
    myHomePath = homePath.getAttributeValue(ATTRIBUTE_VALUE);
    Element elementRoots = assertNotMissing(element, ELEMENT_ROOTS);
    myRoots.readExternal(elementRoots);

    final Element additional = element.getChild(ELEMENT_ADDITIONAL);
    if (additional != null) {
      LOG.assertTrue(mySdkType != null);
      myAdditionalData = mySdkType.loadAdditionalData(this, additional);
    }
    else {
      myAdditionalData = null;
    }
  }

  @NotNull
  private static Element assertNotMissing(@NotNull Element parent, @NotNull String childName) {
    Element child = parent.getChild(childName);
    if (child == null) throw new InvalidDataException("mandatory element '" + childName + "' is missing: " + parent);
    return child;
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    element.setAttribute(ELEMENT_VERSION, "2");

    final Element name = new Element(ELEMENT_NAME);
    name.setAttribute(ATTRIBUTE_VALUE, myName);
    element.addContent(name);

    if (mySdkType != null) {
      final Element sdkType = new Element(ELEMENT_TYPE);
      sdkType.setAttribute(ATTRIBUTE_VALUE, mySdkType.getName());
      element.addContent(sdkType);
    }

    if (myVersionString != null) {
      final Element version = new Element(ELEMENT_VERSION);
      version.setAttribute(ATTRIBUTE_VALUE, myVersionString);
      element.addContent(version);
    }

    final Element home = new Element(ELEMENT_HOMEPATH);
    home.setAttribute(ATTRIBUTE_VALUE, myHomePath);
    element.addContent(home);

    Element roots = new Element(ELEMENT_ROOTS);
    myRoots.writeExternal(roots);
    element.addContent(roots);

    Element additional = new Element(ELEMENT_ADDITIONAL);
    if (myAdditionalData != null) {
      LOG.assertTrue(mySdkType != null);
      mySdkType.saveAdditionalData(myAdditionalData, additional);
    }
    element.addContent(additional);
  }

  @Override
  public void setHomePath(String path) {
    final boolean changes = myHomePath == null ? path != null : !myHomePath.equals(path);
    myHomePath = path;
    if (changes) {
      resetVersionString(); // clear cached value if home path changed
    }
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  @NotNull
  public LegacyProjectJdkDelegate clone() {
    LegacyProjectJdkDelegate newJdk = new LegacyProjectJdkDelegate("", mySdkType);
    copyTo(newJdk);
    return newJdk;
  }

  @Override
  @NotNull
  public RootProvider getRootProvider() {
    return myRootProvider;
  }

  void copyTo(@NotNull LegacyProjectJdkDelegate dest) {
    final String name = getName();
    dest.setName(name);
    dest.setHomePath(getHomePath());
    dest.myVersionDefined = myVersionDefined;
    dest.myVersionString = myVersionString;
    dest.setSdkAdditionalData(getSdkAdditionalData());
    dest.myRoots.copyRootsFrom(myRoots);
    dest.myRootProvider.rootsChanged();
  }

  @Override
  @ApiStatus.Internal
  public void changeType(@NotNull SdkTypeId newType, @Nullable Element additionalDataElement) {
    ThreadingAssertions.assertWriteAccess();
    mySdkType = newType;
    myAdditionalData = additionalDataElement != null ? mySdkType.loadAdditionalData(this, additionalDataElement) : null;
  }

  private class MyRootProvider extends RootProviderBaseImpl implements ProjectRootListener, Supplier<Sdk> {
    @Override
    public String @NotNull [] getUrls(@NotNull OrderRootType rootType) {
      return myRoots.getUrls(rootType);
    }

    @Override
    public VirtualFile @NotNull [] getFiles(@NotNull final OrderRootType rootType) {
      return myRoots.getFiles(rootType);
    }

    @Override
    public void rootsChanged() {
      if (myDispatcher.hasListeners()) {
        ApplicationManager.getApplication().runWriteAction(this::fireRootSetChanged);
      }
    }

    @Override
    public Sdk get() {
      return LegacyProjectJdkDelegate.this;
    }
  }

  // SdkModificator implementation
  @Override
  @NotNull
  public SdkModificator getSdkModificator() {
    LegacyProjectJdkDelegate sdk = clone();
    sdk.myOrigin = this;
    return sdk;
  }

  @Override
  public void commitChanges() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    applyChanges();
  }

  @Override
  public void applyChangesWithoutWriteAction() {
    applyChanges();
  }

  private void applyChanges() {
    LOG.assertTrue(isWritable());

    copyTo(myOrigin);
    myOrigin = null;
    Disposer.dispose(this);
  }

  @Override
  public SdkAdditionalData getSdkAdditionalData() {
    return myAdditionalData;
  }

  @Override
  public void setSdkAdditionalData(SdkAdditionalData data) {
    myAdditionalData = data;
  }

  @Override
  public VirtualFile @NotNull [] getRoots(@NotNull OrderRootType rootType) {
    return myRoots.getFiles(rootType);
  }

  @Override
  public String @NotNull [] getUrls(@NotNull OrderRootType rootType) {
    return myRoots.getUrls(rootType);
  }

  @Override
  public void addRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) {
    myRoots.addRoot(root, rootType);
  }

  @Override
  public void addRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    myRoots.addRoot(url, rootType);
  }

  @Override
  public void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) {
    myRoots.removeRoot(root, rootType);
  }

  @Override
  public void removeRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    myRoots.removeRoot(url, rootType);
  }

  @Override
  public void removeRoots(@NotNull OrderRootType rootType) {
    myRoots.removeAllRoots(rootType);
  }

  @Override
  public void removeAllRoots() {
    myRoots.removeAllRoots();
  }

  @Override
  public boolean isWritable() {
    return myOrigin != null;
  }

  @Override
  public String toString() {
    return myName + (myVersionDefined ? ": " + myVersionString : "") + " (" + myHomePath + ")";
  }
}
