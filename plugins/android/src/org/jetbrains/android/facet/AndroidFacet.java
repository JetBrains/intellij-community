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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import org.jetbrains.android.compiler.AndroidAptCompiler;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.compiler.AndroidIdlCompiler;
import org.jetbrains.android.compiler.AndroidRenderscriptCompiler;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.android.sdk.*;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static org.jetbrains.android.util.AndroidUtils.EMULATOR;
import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

/**
 * @author yole
 */
public class AndroidFacet extends Facet<AndroidFacetConfiguration> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.facet.AndroidFacet");

  public static final FacetTypeId<AndroidFacet> ID = new FacetTypeId<AndroidFacet>("android");
  private VirtualFileAdapter myListener;

  private AvdManager myAvdManager = null;

  private SystemResourceManager mySystemResourceManager;
  private LocalResourceManager myLocalResourceManager;

  private final Map<String, Map<String, PsiClass>> myClassMaps = new HashMap<String, Map<String, PsiClass>>();

  private final Object myClassMapLock = new Object();

  public AndroidFacet(@NotNull Module module, String name, @NotNull AndroidFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
    configuration.setFacet(this);
  }

  @Nullable
  public static String getOutputPackage(@NotNull Module module) {
    VirtualFile compilerOutput = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
    if (compilerOutput == null) return null;
    return new File(compilerOutput.getPath(), getApkName(module)).getPath();
  }

  public static String getApkName(Module module) {
    return module.getName() + ".apk";
  }

  public void androidPlatformChanged() {
    myAvdManager = null;
    myLocalResourceManager = null;
    mySystemResourceManager = null;
  }

  // can be invoked only from dispatch thread!
  @Nullable
  public AndroidDebugBridge getDebugBridge() {
    AndroidPlatform platform = getConfiguration().getAndroidPlatform();
    if (platform != null) {
      return platform.getSdk().getDebugBridge(getModule().getProject());
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
      return getAvdManager();
    }
    catch (AvdsNotSupportedException ignored) {
    }
    catch (AndroidLocation.AndroidLocationException ignored) {
    }
    return null;
  }

  @NotNull
  public AvdManager getAvdManager() throws AvdsNotSupportedException, AndroidLocation.AndroidLocationException {
    if (myAvdManager == null) {
      AndroidPlatform platform = getConfiguration().getAndroidPlatform();
      AndroidSdk sdk = platform != null ? platform.getSdk() : null;
      Project project = getModule().getProject();
      if (sdk instanceof AndroidSdkImpl) {
        SdkManager sdkManager = ((AndroidSdkImpl)sdk).getSdkManager();
        myAvdManager = new AvdManager(sdkManager, AndroidUtils.getSdkLog(project));
      }
      else {
        throw new AvdsNotSupportedException();
      }
    }
    return myAvdManager;
  }

  public void launchEmulator(@Nullable final String avdName, @NotNull final String commands, @Nullable ProcessHandler handler) {
    AndroidPlatform platform = getConfiguration().getAndroidPlatform();
    if (platform != null) {
      final String emulatorPath = platform.getSdk().getLocation() + File.separator + AndroidUtils.toolPath(EMULATOR);
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
      if (handler != null) {
        handler.notifyTextAvailable(commandLine.getCommandLineString() + '\n', ProcessOutputTypes.STDOUT);
      }
      AndroidUtils.runExternalToolInSeparateThread(getModule().getProject(), commandLine, handler);
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
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            Module module = getModule();
            Project project = module.getProject();
            if (project.isDisposed()) {
              return;
            }
            if (getConfiguration().REGENERATE_R_JAVA && AndroidAptCompiler.isToCompileModule(module, getConfiguration())) {
              AndroidCompileUtil.generate(module, new AndroidAptCompiler());
            }
            if (getConfiguration().REGENERATE_JAVA_BY_AIDL) {
              AndroidCompileUtil.generate(module, new AndroidIdlCompiler(project));
            }
            AndroidCompileUtil.generate(module, new AndroidRenderscriptCompiler());
          }
        });
      }
    });

    getModule().getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      private Sdk myPrevSdk;
      private String[] myDependencies;

      public void beforeRootsChange(final ModuleRootEvent event) {
      }

      public void rootsChanged(final ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (isDisposed()) {
              return;
            }

            final Sdk newSdk = ModuleRootManager.getInstance(getModule()).getSdk();
            if (newSdk != null && newSdk.getSdkType() instanceof AndroidSdkType && !newSdk.equals(myPrevSdk)) {
              myPrevSdk = newSdk;
              androidPlatformChanged();
            }

            PsiDocumentManager.getInstance(getModule().getProject()).commitAllDocuments();

            final PropertiesFile propertiesFile = AndroidUtils.findPropertyFile(getModule(), SdkConstants.FN_PROJECT_PROPERTIES);
            if (propertiesFile == null) {
              return;
            }

            updateTargetProperty(propertiesFile);
            updateLibraryProperty(propertiesFile);

            final String[] dependencies = collectDependencies();
            if (myDependencies == null || !Comparing.equal(myDependencies, dependencies)) {
              updateDependenciesInPropertyFile(propertiesFile, dependencies);
              myDependencies = dependencies;
            }
          }
        });
      }
    });
  }

  private void updateDependenciesInPropertyFile(@NotNull final PropertiesFile propertiesFile, @NotNull final String[] dependencies) {
    final VirtualFile vFile = propertiesFile.getVirtualFile();
    if (vFile == null) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (IProperty property : propertiesFile.getProperties()) {
          final String name = property.getName();
          if (name != null && name.startsWith(AndroidUtils.ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX)) {
            property.getPsiElement().delete();
          }
        }

        final VirtualFile baseDir = vFile.getParent();
        final String baseDirPath = baseDir.getPath();
        final Project project = getModule().getProject();

        int index = 1;
        for (String dependency : dependencies) {
          final String relPath = FileUtil.getRelativePath(baseDirPath, dependency, '/');
          final String value = relPath != null ? relPath : dependency;
          propertiesFile.addProperty(AndroidUtils.ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX + index, value);
          index++;
        }
      }
    });
  }

  private String[] collectDependencies() {
    final List<String> dependenciesList = new ArrayList<String>();

    for (AndroidFacet depFacet : AndroidUtils.getAndroidDependencies(getModule(), true)) {
      final Module depModule = depFacet.getModule();
      final VirtualFile libDir = getBaseAndroidContentRoot(depModule);
      if (libDir != null) {
        dependenciesList.add(libDir.getPath());
      }
    }

    final String[] dependencies = ArrayUtil.toStringArray(dependenciesList);
    Arrays.sort(dependencies);
    return dependencies;
  }

  @Nullable
  private static VirtualFile getBaseAndroidContentRoot(@NotNull Module module) {
    final VirtualFile manifestFile = AndroidRootUtil.getManifestFile(module);
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (manifestFile != null) {
      for (VirtualFile contentRoot : contentRoots) {
        if (VfsUtil.isAncestor(contentRoot, manifestFile, true)) {
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
            propertiesFile.addProperty(AndroidUtils.ANDROID_TARGET_PROPERTY, targetPropertyValue);
          }
          else {
            property.setValue(targetPropertyValue);
          }
        }
      });
    }
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

    if (AndroidCompileUtil.isGenModule(module)) {
      final Module baseModule = AndroidCompileUtil.getBaseModuleByGenModule(module);
      if (baseModule != null) {
        module = baseModule;
      }
    }

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
      myLocalResourceManager = new LocalResourceManager(getModule());
    }
    return myLocalResourceManager;
  }

  @Nullable
  public SystemResourceManager getSystemResourceManager() {
    if (mySystemResourceManager == null) {
      AndroidPlatform platform = getConfiguration().getAndroidPlatform();
      if (platform != null) {
        mySystemResourceManager = new SystemResourceManager(getModule(), platform);
      }
    }
    return mySystemResourceManager;
  }

  @Nullable
  public Manifest getManifest() {
    final VirtualFile manifestFile = AndroidRootUtil.getManifestFile(getModule());
    if (manifestFile == null) return null;
    return AndroidUtils.loadDomElement(getModule(), manifestFile, Manifest.class);
  }

  public static AndroidFacetType getFacetType() {
    return (AndroidFacetType)FacetTypeRegistry.getInstance().findFacetType(ID);
  }

  public PsiClass findClass(final String className) {
    return findClass(className, getModule().getModuleWithDependenciesAndLibrariesScope(true));
  }

  public PsiClass findClass(final String className, final GlobalSearchScope scope) {
    final Project project = getModule().getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Nullable
      public PsiClass compute() {
        return facade.findClass(className, scope);
      }
    });
  }

  @NotNull
  public Map<String, PsiClass> getClassMap(@NotNull String className, @NotNull ClassMapConstructor constructor) {
    synchronized (myClassMapLock) {
      Map<String, PsiClass> classMap = getInitialClassMap(className, constructor);
      Map<String, PsiClass> result = new HashMap<String, PsiClass>();
      for (Iterator<String> it = classMap.keySet().iterator(); it.hasNext();) {
        String key = it.next();
        PsiClass value = classMap.get(key);
        String[] tagNames = constructor.getTagNamesByClass(value);
        if (ArrayUtil.find(tagNames, key) < 0) {
          it.remove();
        }
        else {
          result.put(key, value);
        }
      }
      Project project = getModule().getProject();
      fillMap(className, constructor, ProjectScope.getProjectScope(project), result);
      return result;
    }
  }

  @NotNull
  private Map<String, PsiClass> getInitialClassMap(@NotNull String className, @NotNull ClassMapConstructor constructor) {
    Map<String, PsiClass> viewClassMap = myClassMaps.get(className);
    if (viewClassMap != null) return viewClassMap;
    viewClassMap = new HashMap<String, PsiClass>();
    if (fillMap(className, constructor, getModule().getModuleWithDependenciesAndLibrariesScope(true), viewClassMap)) {
      myClassMaps.put(className, viewClassMap);
    }
    return viewClassMap;
  }

  private boolean fillMap(@NotNull String className,
                          @NotNull final ClassMapConstructor constructor,
                          GlobalSearchScope scope,
                          final Map<String, PsiClass> map) {
    PsiClass baseClass = findClass(className, getModule().getModuleWithDependenciesAndLibrariesScope(true));
    if (baseClass != null) {
      String[] baseClassTagNames = constructor.getTagNamesByClass(baseClass);
      for (String tagName : baseClassTagNames) {
        map.put(tagName, baseClass);
      }
      try {
        ClassInheritorsSearch.search(baseClass, scope, true).forEach(new Processor<PsiClass>() {
          public boolean process(PsiClass c) {
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

  @Nullable
  public String getAptGenSourceRootPath() {
    String path = getConfiguration().GEN_FOLDER_RELATIVE_PATH_APT;
    if (path.length() == 0) return null;
    String moduleDirPath = getModuleDirPath();
    return moduleDirPath != null ? moduleDirPath + path : null;
  }


  @Nullable
  public String getAidlGenSourceRootPath() {
    String path = getConfiguration().GEN_FOLDER_RELATIVE_PATH_AIDL;
    if (path.length() == 0) return null;
    String moduleDirPath = getModuleDirPath();
    return moduleDirPath != null ? moduleDirPath + path : null;
  }

  @Nullable
  public String getModuleDirPath() {
    return AndroidRootUtil.getModuleDirPath(getModule());
  }

  @Nullable
  public String getApkPath() {
    String path = getConfiguration().APK_PATH;
    if (path.length() == 0) {
      return getOutputPackage(getModule());
    }
    String moduleDirPath = getModuleDirPath();
    return moduleDirPath != null ? FileUtil.toSystemDependentName(moduleDirPath + path) : null;
  }
}
