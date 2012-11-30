/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.facet;

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.utils.ILogger;
import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import org.jetbrains.android.compiler.AndroidAptCompiler;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.importDependencies.ImportDependenciesUtil;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.android.sdk.*;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

/**
 * @author yole
 */
public class AndroidFacet extends Facet<AndroidFacetConfiguration> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.facet.AndroidFacet");

  public static final FacetTypeId<AndroidFacet> ID = new FacetTypeId<AndroidFacet>("android");
  private AndroidResourceFilesListener myListener;

  private AvdManager myAvdManager = null;

  private SystemResourceManager mySystemResourceManager;
  private LocalResourceManager myLocalResourceManager;

  private final Map<String, Map<String, SmartPsiElementPointer<PsiClass>>> myClassMaps =
    new HashMap<String, Map<String, SmartPsiElementPointer<PsiClass>>>();

  private final Object myClassMapLock = new Object();

  private final Set<AndroidAutogeneratorMode> myDirtyModes = EnumSet.noneOf(AndroidAutogeneratorMode.class);
  private final Set<AndroidAutogeneratorMode> myGeneratedWithErrorsModes = EnumSet.noneOf(AndroidAutogeneratorMode.class);
  private final Map<AndroidAutogeneratorMode, Set<String>> myAutogeneratedFiles = new HashMap<AndroidAutogeneratorMode, Set<String>>();

  private volatile boolean myAutogenerationEnabled = false;

  public AndroidFacet(@NotNull Module module, String name, @NotNull AndroidFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
    configuration.setFacet(this);

    for (AndroidAutogeneratorMode mode : AndroidAutogeneratorMode.values()) {
      createAlarm(mode);
    }
  }

  private Alarm createAlarm(@NotNull final AndroidAutogeneratorMode mode) {
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, this);
    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
        boolean regenerate;

        synchronized (myDirtyModes) {
          regenerate = myDirtyModes.contains(mode);
        }
        regenerate = regenerate || isGeneratedFileRemoved(mode);

        if (myAutogenerationEnabled && regenerate) {
          final boolean result = AndroidCompileUtil.doGenerate(getModule(), mode);

          synchronized (myDirtyModes) {
            myDirtyModes.remove(mode);

            if (result) {
              myGeneratedWithErrorsModes.remove(mode);
            }
            else {
              myGeneratedWithErrorsModes.add(mode);
            }
          }
        }
        if (!alarm.isDisposed()) {
          alarm.addRequest(this, 2000);
        }
      }
    }, 2000);
    return alarm;
  }

  private boolean isGeneratedFileRemoved(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myAutogeneratedFiles) {
      final Set<String> filePaths = myAutogeneratedFiles.get(mode);

      if (filePaths != null) {
        for (String path : filePaths) {
          final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);

          if (file == null) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public void clearAutogeneratedFiles(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myAutogeneratedFiles) {
      final Set<String> set = myAutogeneratedFiles.get(mode);
      if (set != null) {
        set.clear();
      }
    }
  }

  public void markFileAutogenerated(@NotNull AndroidAutogeneratorMode mode, @NotNull VirtualFile file) {
    synchronized (myAutogeneratedFiles) {
      Set<String> set = myAutogeneratedFiles.get(mode);

      if (set == null) {
        set = new HashSet<String>();
        myAutogeneratedFiles.put(mode, set);
      }
      set.add(file.getPath());
    }
  }

  @NotNull
  public Set<String> getAutogeneratedFiles(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myAutogeneratedFiles) {
      final Set<String> set = myAutogeneratedFiles.get(mode);
      return set != null ? new HashSet<String>(set) : Collections.<String>emptySet();
    }
  }

  private void activateSourceAutogenerating() {
    myAutogenerationEnabled = true;
  }

  public void androidPlatformChanged() {
    myAvdManager = null;
    myLocalResourceManager = null;
    mySystemResourceManager = null;
    myClassMaps.clear();
  }

  // can be invoked only from dispatch thread!
  @Nullable
  public AndroidDebugBridge getDebugBridge() {
    AndroidPlatform platform = getConfiguration().getAndroidPlatform();
    if (platform != null) {
      return platform.getSdkData().getDebugBridge(getModule().getProject());
    }
    return null;
  }

  public AvdInfo[] getAllAvds() {
    AvdManager manager = getAvdManagerSilently();
    if (manager != null) {
      if (reloadAvds(manager)) {
        return manager.getAllAvds();
      }
    }
    return new AvdInfo[0];
  }

  private boolean reloadAvds(AvdManager manager) {
    try {
      MessageBuildingSdkLog log = new MessageBuildingSdkLog();
      manager.reloadAvds(log);
      if (log.getErrorMessage().length() > 0) {
        Messages
          .showErrorDialog(getModule().getProject(), AndroidBundle.message("cant.load.avds.error.prefix") + ' ' + log.getErrorMessage(),
                           CommonBundle.getErrorTitle());
      }
      return true;
    }
    catch (AndroidLocation.AndroidLocationException e) {
      Messages.showErrorDialog(getModule().getProject(), AndroidBundle.message("cant.load.avds.error"), CommonBundle.getErrorTitle());
    }
    return false;
  }

  public AvdInfo[] getAllCompatibleAvds() {
    List<AvdInfo> result = new ArrayList<AvdInfo>();
    addCompatibleAvds(result, getAllAvds());
    return result.toArray(new AvdInfo[result.size()]);
  }

  public AvdInfo[] getValidCompatibleAvds() {
    AvdManager manager = getAvdManagerSilently();
    List<AvdInfo> result = new ArrayList<AvdInfo>();
    if (manager != null && reloadAvds(manager)) {
      addCompatibleAvds(result, manager.getValidAvds());
    }
    return result.toArray(new AvdInfo[result.size()]);
  }

  private AvdInfo[] addCompatibleAvds(List<AvdInfo> to, @NotNull AvdInfo[] from) {
    for (AvdInfo avd : from) {
      if (isCompatibleAvd(avd)) {
        to.add(avd);
      }
    }
    return to.toArray(new AvdInfo[to.size()]);
  }

  @Nullable
  private static AndroidVersion getDeviceVersion(IDevice device) {
    try {
      Map<String, String> props = device.getProperties();
      String apiLevel = props.get(IDevice.PROP_BUILD_API_LEVEL);
      if (apiLevel == null) {
        return null;
      }

      return new AndroidVersion(Integer.parseInt(apiLevel), props.get((IDevice.PROP_BUILD_CODENAME)));
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Nullable
  public Boolean isCompatibleDevice(@NotNull IDevice device) {
    String avd = device.getAvdName();
    IAndroidTarget target = getConfiguration().getAndroidTarget();
    if (target == null) return false;
    if (avd != null) {
      AvdManager avdManager = getAvdManagerSilently();
      if (avdManager == null) return true;
      AvdInfo info = avdManager.getAvd(avd, true);
      return isCompatibleBaseTarget(info != null ? info.getTarget() : null);
    }
    if (target.isPlatform()) {
      AndroidVersion deviceVersion = getDeviceVersion(device);
      if (deviceVersion != null) {
        return canRunOnDevice(target, deviceVersion);
      }
    }
    return null;
  }

  // if baseTarget is null, then function return if application can be deployed on any target

  public boolean isCompatibleBaseTarget(@Nullable IAndroidTarget baseTarget) {
    IAndroidTarget target = getConfiguration().getAndroidTarget();
    if (target == null) return false;
    AndroidVersion baseTargetVersion = baseTarget != null ? baseTarget.getVersion() : null;
    if (!canRunOnDevice(target, baseTargetVersion)) return false;
    if (!target.isPlatform()) {
      if (baseTarget == null) return false;
      // then it is add-on
      if (!Comparing.equal(target.getVendor(), baseTarget.getVendor()) || !Comparing.equal(target.getName(), baseTarget.getName())) {
        return false;
      }
    }
    return true;
  }

  private boolean canRunOnDevice(IAndroidTarget projectTarget, AndroidVersion deviceVersion) {
    int minSdkVersion = -1;
    int maxSdkVersion = -1;
    final Manifest manifest = getManifest();
    if (manifest != null) {
      XmlTag manifestTag = ApplicationManager.getApplication().runReadAction(new Computable<XmlTag>() {
        @Override
        public XmlTag compute() {
          return manifest.getXmlTag();
        }
      });
      if (manifestTag != null) {
        XmlTag[] tags = manifestTag.findSubTags("uses-sdk");
        for (XmlTag tag : tags) {
          int candidate = AndroidUtils.getIntAttrValue(tag, "minSdkVersion");
          if (candidate >= 0) minSdkVersion = candidate;
          candidate = AndroidUtils.getIntAttrValue(tag, "maxSdkVersion");
          if (candidate >= 0) maxSdkVersion = candidate;
        }
      }
    }

    int baseApiLevel = deviceVersion != null ? deviceVersion.getApiLevel() : 1;
    AndroidVersion targetVersion = projectTarget.getVersion();
    if (minSdkVersion < 0) minSdkVersion = targetVersion.getApiLevel();
    if (minSdkVersion > baseApiLevel) return false;
    if (maxSdkVersion >= 0 && maxSdkVersion < baseApiLevel) return false;
    String codeName = targetVersion.getCodename();
    String baseCodeName = deviceVersion != null ? deviceVersion.getCodename() : null;
    if (codeName != null && !codeName.equals(baseCodeName)) {
      return false;
    }
    return true;
  }

  public boolean isCompatibleAvd(@NotNull AvdInfo avd) {
    IAndroidTarget target = getConfiguration().getAndroidTarget();
    return target != null && avd.getTarget() != null && isCompatibleBaseTarget(avd.getTarget());
  }

  @Nullable
  public AvdManager getAvdManagerSilently() {
    try {
      return getAvdManager(new AvdManagerLog());
    }
    catch (AvdsNotSupportedException ignored) {
    }
    catch (AndroidLocation.AndroidLocationException ignored) {
    }
    return null;
  }

  @NotNull
  public AvdManager getAvdManager(ILogger log) throws AvdsNotSupportedException, AndroidLocation.AndroidLocationException {
    if (myAvdManager == null) {
      AndroidPlatform platform = getConfiguration().getAndroidPlatform();
      AndroidSdkData sdkData = platform != null ? platform.getSdkData() : null;

      if (sdkData != null) {
        SdkManager sdkManager = sdkData.getSdkManager();
        myAvdManager = AvdManager.getInstance(sdkManager, log);
      }
      else {
        throw new AvdsNotSupportedException();
      }
    }
    return myAvdManager;
  }

  public void launchEmulator(@Nullable final String avdName, @NotNull final String commands, @NotNull final ProcessHandler handler) {
    AndroidPlatform platform = getConfiguration().getAndroidPlatform();
    if (platform != null) {
      final String emulatorPath = platform.getSdkData().getLocation() + File.separator + AndroidCommonUtils
        .toolPath(SdkConstants.FN_EMULATOR);
      final GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(FileUtil.toSystemDependentName(emulatorPath));
      if (avdName != null) {
        commandLine.addParameter("-avd");
        commandLine.addParameter(avdName);
      }
      String[] params = ParametersList.parse(commands);
      for (String s : params) {
        if (s.length() > 0) {
          commandLine.addParameter(s);
        }
      }
      handler.notifyTextAvailable(commandLine.getCommandLineString() + '\n', ProcessOutputTypes.STDOUT);

      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          try {
            AndroidUtils.executeCommand(commandLine, new OutputProcessor() {
              @Override
              public void onTextAvailable(@NotNull String text) {
                handler.notifyTextAvailable(text, ProcessOutputTypes.STDOUT);
              }
            }, WaitingStrategies.WaitForTime.getInstance(5000));
          }
          catch (ExecutionException e) {
            final String stackTrace = AndroidCommonUtils.getStackTrace(e);
            handler.notifyTextAvailable(stackTrace, ProcessOutputTypes.STDERR);
          }
        }
      });
    }
  }

  @Override
  public void initFacet() {
    StartupManager.getInstance(getModule().getProject()).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        myListener = new AndroidResourceFilesListener(AndroidFacet.this);
        LocalFileSystem.getInstance().addVirtualFileListener(myListener);
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          return;
        }

        addResourceFolderToSdkRootsIfNecessary();
        
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            Module module = getModule();
            Project project = module.getProject();
            if (project.isDisposed()) {
              return;
            }

            if (AndroidAptCompiler.isToCompileModule(module, getConfiguration())) {
              AndroidCompileUtil.generate(module, AndroidAutogeneratorMode.AAPT);
            }
            AndroidCompileUtil.generate(module, AndroidAutogeneratorMode.AIDL);
            AndroidCompileUtil.generate(module, AndroidAutogeneratorMode.RENDERSCRIPT);
            AndroidCompileUtil.generate(module, AndroidAutogeneratorMode.BUILDCONFIG);

            activateSourceAutogenerating();
          }
        });
      }
    });

    getModule().getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      private Sdk myPrevSdk;
      private String[] myDependencies;

      public void rootsChanged(final ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (isDisposed()) {
              return;
            }
            final ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());

            final Sdk newSdk = rootManager.getSdk();
            if (newSdk != null && newSdk.getSdkType() instanceof AndroidSdkType && !newSdk.equals(myPrevSdk)) {
              androidPlatformChanged();

              synchronized (myDirtyModes) {
                myDirtyModes.addAll(Arrays.asList(AndroidAutogeneratorMode.values()));
              }
            }
            myPrevSdk = newSdk;

            PsiDocumentManager.getInstance(getModule().getProject()).commitAllDocuments();

            final PropertiesFile projectProperties = AndroidRootUtil.findPropertyFile(getModule(), SdkConstants.FN_PROJECT_PROPERTIES);
            if (projectProperties == null) {
              return;
            }
            final Pair<Properties, VirtualFile> localProperties = 
              AndroidRootUtil.readPropertyFile(getModule(), SdkConstants.FN_LOCAL_PROPERTIES);

            updateTargetProperty(projectProperties);
            updateLibraryProperty(projectProperties);

            final VirtualFile[] dependencies = collectDependencies();
            final String[] dependencyPaths = toSortedPaths(dependencies);

            if (myDependencies == null || !Comparing.equal(myDependencies, dependencyPaths)) {
              updateDependenciesInPropertyFile(projectProperties, localProperties, dependencies);
              myDependencies = dependencyPaths;
            }
          }
        });
      }
    });
  }
  
  private void addResourceFolderToSdkRootsIfNecessary() {
    final Sdk sdk = ModuleRootManager.getInstance(getModule()).getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      return;
    }

    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (!(data instanceof AndroidSdkAdditionalData)) {
      return;
    }

    final AndroidPlatform platform = ((AndroidSdkAdditionalData)data).getAndroidPlatform();
    if (platform == null) {
      return;
    }

    final String resFolderPath = platform.getTarget().getPath(IAndroidTarget.RESOURCES);
    if (resFolderPath == null) {
      return;
    }
    final List<VirtualFile> filesToAdd = new ArrayList<VirtualFile>();

    final VirtualFile resFolder = LocalFileSystem.getInstance().findFileByPath(resFolderPath);
    if (resFolder != null) {
      filesToAdd.add(resFolder);
    }

    if (platform.needToAddAnnotationsJarToClasspath()) {
      final String sdkHomePath = FileUtil.toSystemIndependentName(platform.getSdkData().getLocation());
      final VirtualFile annotationsJar = JarFileSystem.getInstance().findFileByPath(
        sdkHomePath + AndroidCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH + JarFileSystem.JAR_SEPARATOR);
      if (annotationsJar != null) {
        filesToAdd.add(annotationsJar);
      }
    }

    addFilesToSdkIfNecessary(sdk, filesToAdd);
  }

  private static void addFilesToSdkIfNecessary(@NotNull Sdk sdk, @NotNull Collection<VirtualFile> files) {
    final List<VirtualFile> newFiles = new ArrayList<VirtualFile>(files);
    newFiles.removeAll(Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES)));

    if (newFiles.size() > 0) {
      final SdkModificator modificator = sdk.getSdkModificator();

      for (VirtualFile file : newFiles) {
        modificator.addRoot(file, OrderRootType.CLASSES);
      }
      modificator.commitChanges();
    }
  }

  private static void updateDependenciesInPropertyFile(@NotNull final PropertiesFile projectProperties,
                                                       @Nullable final Pair<Properties, VirtualFile> localProperties,
                                                       @NotNull final VirtualFile[] dependencies) {
    final VirtualFile vFile = projectProperties.getVirtualFile();
    if (vFile == null) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (IProperty property : projectProperties.getProperties()) {
          final String name = property.getName();
          if (name != null && name.startsWith(AndroidUtils.ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX)) {
            property.getPsiElement().delete();
          }
        }

        final VirtualFile baseDir = vFile.getParent();
        final String baseDirPath = baseDir.getPath();
        final Set<VirtualFile> localDependencies = localProperties != null
                                                   ? ImportDependenciesUtil.getLibDirs(localProperties)
                                                   : Collections.<VirtualFile>emptySet();
        int index = 1;
        for (VirtualFile dependency : dependencies) {
          if (!localDependencies.contains(dependency)) {
            final String relPath = FileUtil.getRelativePath(baseDirPath, dependency.getPath(), '/');
            final String value = relPath != null ? relPath : dependency.getPath();
            projectProperties.addProperty(AndroidUtils.ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX + index, value);
            index++;
          }
        }
      }
    });
  }

  @NotNull
  private VirtualFile[] collectDependencies() {
    final List<VirtualFile> dependenciesList = new ArrayList<VirtualFile>();

    for (AndroidFacet depFacet : AndroidUtils.getAndroidLibraryDependencies(getModule())) {
      final Module depModule = depFacet.getModule();
      final VirtualFile libDir = getBaseAndroidContentRoot(depModule);
      if (libDir != null) {
        dependenciesList.add(libDir);
      }
    }
    return dependenciesList.toArray(new VirtualFile[dependenciesList.size()]);
  }

  @NotNull
  private static String[] toSortedPaths(@NotNull VirtualFile[] files) {
    final String[] result = new String[files.length];
    
    for (int i = 0; i < files.length; i++) {
      result[i] = files[i].getPath();
    }
    Arrays.sort(result);
    return result;
  }

  @Nullable
  private static VirtualFile getBaseAndroidContentRoot(@NotNull Module module) {
    final AndroidFacet facet = getInstance(module);
    final VirtualFile manifestFile = facet != null ? AndroidRootUtil.getManifestFile(facet) : null;
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (manifestFile != null) {
      for (VirtualFile contentRoot : contentRoots) {
        if (VfsUtilCore.isAncestor(contentRoot, manifestFile, true)) {
          return contentRoot;
        }
      }
    }
    return contentRoots.length > 0 ? contentRoots[0] : null;
  }

  private void updateTargetProperty(@NotNull final PropertiesFile propertiesFile) {
    final IAndroidTarget androidTarget = getConfiguration().getAndroidTarget();
    if (androidTarget != null) {
      final String targetPropertyValue = androidTarget.hashString();
      final IProperty property = propertiesFile.findPropertyByKey(AndroidUtils.ANDROID_TARGET_PROPERTY);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          if (property == null) {
            propertiesFile.addProperty(createProperty(targetPropertyValue));
          }
          else {
            if (!Comparing.equal(property.getValue(), targetPropertyValue)) {
              final PsiElement element = property.getPsiElement();
              if (element != null) {
                element.replace(createProperty(targetPropertyValue).getPsiElement());
              }
            }
          }
        }
      });
    }
  }

  // workaround for behavior of Android SDK , which uses non-escaped ':' characters
  @NotNull
  private IProperty createProperty(String targetPropertyValue) {
    final String text = AndroidUtils.ANDROID_TARGET_PROPERTY + "=" + targetPropertyValue;
    final PropertiesFile dummyFile = PropertiesElementFactory.createPropertiesFile(getModule().getProject(), text);
    return dummyFile.getProperties().get(0);
  }

  public void updateLibraryProperty(@NotNull final PropertiesFile propertiesFile) {
    final IProperty property = propertiesFile.findPropertyByKey(AndroidUtils.ANDROID_LIBRARY_PROPERTY);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (property != null) {
          property.setValue(Boolean.toString(getConfiguration().LIBRARY_PROJECT));
        }
        else if (getConfiguration().LIBRARY_PROJECT) {
          propertiesFile.addProperty(AndroidUtils.ANDROID_LIBRARY_PROPERTY, Boolean.TRUE.toString());
        }
      }
    });
  }

  @Override
  public void disposeFacet() {
    if (myListener != null) {
      LocalFileSystem.getInstance().removeVirtualFileListener(myListener);
    }
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull Module module) {
    return FacetManager.getInstance(module).getFacetByType(ID);
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull ConvertContext context) {
    Module module = context.getModule();
    return module != null ? getInstance(module) : null;
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull final PsiElement element) {
    Module module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      @Nullable
      @Override
      public Module compute() {
        return ModuleUtil.findModuleForPsiElement(element);
      }
    });
    if (module == null) return null;
    return getInstance(module);
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull DomElement element) {
    Module module = element.getModule();
    if (module == null) return null;
    return getInstance(module);
  }

  @Nullable
  public ResourceManager getResourceManager(@Nullable String resourcePackage) {
    return SYSTEM_RESOURCE_PACKAGE.equals(resourcePackage) ? getSystemResourceManager() : getLocalResourceManager();
  }

  @NotNull
  public LocalResourceManager getLocalResourceManager() {
    if (myLocalResourceManager == null) {
      myLocalResourceManager = new LocalResourceManager(this);
    }
    return myLocalResourceManager;
  }

  @Nullable
  public SystemResourceManager getSystemResourceManager() {
    if (mySystemResourceManager == null) {
      AndroidPlatform platform = getConfiguration().getAndroidPlatform();
      if (platform != null) {
        mySystemResourceManager = new SystemResourceManager(this, platform);
      }
    }
    return mySystemResourceManager;
  }

  @Nullable
  public Manifest getManifest() {
    final VirtualFile manifestFile = AndroidRootUtil.getManifestFile(this);
    if (manifestFile == null) return null;
    return AndroidUtils.loadDomElement(getModule(), manifestFile, Manifest.class);
  }

  public static AndroidFacetType getFacetType() {
    return (AndroidFacetType)FacetTypeRegistry.getInstance().findFacetType(ID);
  }

  // todo: correctly support classes from external non-platform jars
  @NotNull
  public Map<String, PsiClass> getClassMap(@NotNull String className, @NotNull ClassMapConstructor constructor) {
    synchronized (myClassMapLock) {
      Map<String, SmartPsiElementPointer<PsiClass>> classMap = getInitialClassMap(className, constructor, false);
      final Map<String, PsiClass> result = new HashMap<String, PsiClass>();
      boolean shouldRebuildInitialMap = false;

      for (final String key : classMap.keySet()) {
        final SmartPsiElementPointer<PsiClass> pointer = classMap.get(key);

        if (!isUpToDate(pointer, key, constructor)) {
          shouldRebuildInitialMap = true;
          break;
        }
        final PsiClass aClass = pointer.getElement();

        if (aClass != null) {
          result.put(key, aClass);
        }
      }

      if (shouldRebuildInitialMap) {
        result.clear();
        classMap = getInitialClassMap(className, constructor, true);

        for (final String key : classMap.keySet()) {
          final SmartPsiElementPointer<PsiClass> pointer = classMap.get(key);
          final PsiClass aClass = pointer.getElement();

          if (aClass != null) {
            result.put(key, aClass);
          }
        }
      }
      final Project project = getModule().getProject();
      fillMap(className, constructor, ProjectScope.getProjectScope(project), result, false);
      return result;
    }
  }

  private static boolean isUpToDate(SmartPsiElementPointer<PsiClass> pointer, String tagName, ClassMapConstructor constructor) {
    final PsiClass aClass = pointer.getElement();
    if (aClass == null) {
      return false;
    }
    final String[] tagNames = constructor.getTagNamesByClass(aClass);
    return ArrayUtil.find(tagNames, tagName) >= 0;
  }

  @NotNull
  private Map<String, SmartPsiElementPointer<PsiClass>> getInitialClassMap(@NotNull String className,
                                                                           @NotNull ClassMapConstructor constructor,
                                                                           boolean forceRebuild) {
    Map<String, SmartPsiElementPointer<PsiClass>> viewClassMap = myClassMaps.get(className);
    if (viewClassMap != null && !forceRebuild) return viewClassMap;
    final HashMap<String, PsiClass> map = new HashMap<String, PsiClass>();

    if (fillMap(className, constructor, getModule().getModuleWithDependenciesAndLibrariesScope(true), map, true)) {
      viewClassMap = new HashMap<String, SmartPsiElementPointer<PsiClass>>(map.size());
      final SmartPointerManager manager = SmartPointerManager.getInstance(getModule().getProject());

      for (Map.Entry<String, PsiClass> entry : map.entrySet()) {
        viewClassMap.put(entry.getKey(), manager.createSmartPsiElementPointer(entry.getValue()));
      }
      myClassMaps.put(className, viewClassMap);
    }
    return viewClassMap != null
           ? viewClassMap
           : Collections.<String, SmartPsiElementPointer<PsiClass>>emptyMap();
  }

  private boolean fillMap(@NotNull final String className,
                          @NotNull final ClassMapConstructor constructor,
                          GlobalSearchScope scope,
                          final Map<String, PsiClass> map,
                          final boolean libClassesOnly) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getModule().getProject());
    final PsiClass baseClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Nullable
      public PsiClass compute() {
        return facade.findClass(className, getModule().getModuleWithDependenciesAndLibrariesScope(true));
      }
    });
    if (baseClass != null) {
      String[] baseClassTagNames = constructor.getTagNamesByClass(baseClass);
      for (String tagName : baseClassTagNames) {
        map.put(tagName, baseClass);
      }
      try {
        ClassInheritorsSearch.search(baseClass, scope, true).forEach(new Processor<PsiClass>() {
          public boolean process(PsiClass c) {
            if (libClassesOnly && c.getManager().isInProject(c)) {
              return true;
            }
            String[] tagNames = constructor.getTagNamesByClass(c);
            for (String tagName : tagNames) {
              map.put(tagName, c);
            }
            return true;
          }
        });
      }
      catch (IndexNotReadyException e) {
        LOG.info(e);
        return false;
      }
    }
    return map.size() > 0;
  }


  public void scheduleSourceRegenerating(@NotNull final AndroidAutogeneratorMode mode) {
    synchronized (myDirtyModes) {
      myDirtyModes.add(mode);
    }
  }

  public boolean areSourcesGeneratedWithErrors(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myDirtyModes) {
      return myGeneratedWithErrorsModes.contains(mode);
    }
  }
}
