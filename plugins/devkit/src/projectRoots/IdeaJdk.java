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
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaDependentSdkType;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.compiled.ClsParsingUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import icons.DevkitIcons;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.serialization.JpsSerializationManager;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author anna
 * @since Nov 22, 2004
 */
public class IdeaJdk extends JavaDependentSdkType implements JavaSdkType {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.projectRoots.IdeaJdk");
  @NonNls private static final String LIB_DIR_NAME = "lib";
  @NonNls private static final String SRC_DIR_NAME = "src";
  @NonNls private static final String PLUGINS_DIR = "plugins";

  public IdeaJdk() {
    super("IDEA JDK");
  }

  @Override
  public Icon getIcon() {
    return DevkitIcons.Sdk_closed;
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "reference.project.structure.sdk.idea";
  }

  @Override
  @NotNull
  public Icon getIconForAddAction() {
    return DevkitIcons.Add_sdk;
  }

  @Override
  public String suggestHomePath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/');
  }

  @NotNull
  @Override
  public String adjustSelectedSdkHome(@NotNull String homePath) {
    if (SystemInfo.isMac) {
      File home = new File(homePath, "Contents");
      if (home.exists()) return home.getPath();
    }
    return super.adjustSelectedSdkHome(homePath);
  }

  @Override
  public boolean isValidSdkHome(String path) {
    if (isFromIDEAProject(path)) {
      return true;
    }
    File home = new File(path);
    return home.exists() && getBuildNumber(path) != null && getPlatformApiJar(path) != null;
  }

  @Nullable
  private static File getPlatformApiJar(String home) {
    final File libDir = new File(home, LIB_DIR_NAME);
    File f = new File(libDir, "platform-api.jar");
    if (f.exists()) return f;
    //in 173.* and earlier builds all IDEs included platform modules into openapi.jar (see org.jetbrains.intellij.build.ProductModulesLayout.platformApiModules)
    f = new File(libDir, "openapi.jar");
    if (f.exists()) return f;
    return null;
  }

  public static boolean isFromIDEAProject(String path) {
    File ultimate = new File(path, "idea.iml");
    File community = new File(path, "community-main.iml");
    return ultimate.exists() && ultimate.isFile() ||
           community.exists() && community.isFile();
  }

  @Override
  @Nullable
  public final String getVersionString(@NotNull final Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk != null ? internalJavaSdk.getVersionString() : null;
  }

  @Nullable
  private static Sdk getInternalJavaSdk(final Sdk sdk) {
    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (data instanceof Sandbox) {
      return ((Sandbox)data).getJavaSdk();
    }
    return null;
  }

  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    if (isFromIDEAProject(sdkHome)) return "Local IDEA [" + sdkHome + "]";
    String buildNumber = getBuildNumber(sdkHome);
    return IntelliJPlatformProduct.fromBuildNumber(buildNumber).getName() + " " + (buildNumber != null ? buildNumber : "");
  }

  @Nullable
  public static String getBuildNumber(String ideaHome) {
    try {
      @NonNls final String buildTxt = SystemInfo.isMac ? "Resources/build.txt" : "build.txt";
      File file = new File(ideaHome, buildTxt);
      if (SystemInfo.isMac && !file.exists()) {
        // IntelliJ IDEA 13 and earlier used a different location for build.txt on Mac;
        // recognize the old location as well
        file = new File(ideaHome, "build.txt");
      }
      return FileUtil.loadFile(file).trim();
    }
    catch (IOException e) {
      return null;
    }
  }

  private static VirtualFile[] getIdeaLibrary(String home) {
    List<VirtualFile> result = new ArrayList<>();
    appendIdeaLibrary(home, result, "junit.jar");
    String plugins = home + File.separator + PLUGINS_DIR + File.separator;
    appendIdeaLibrary(plugins + "JavaEE", result, "javaee-impl.jar", "jpa-console.jar");
    appendIdeaLibrary(plugins + "PersistenceSupport", result, "persistence-impl.jar");
    appendIdeaLibrary(plugins + "DatabaseTools", result, "database-impl.jar", "jdbc-console.jar");
    appendIdeaLibrary(plugins + "css", result, "css.jar");
    appendIdeaLibrary(plugins + "uml", result, "uml-support.jar");
    appendIdeaLibrary(plugins + "Spring", result,
                      "spring.jar", "spring-el.jar", "spring-jsf.jar", "spring-persistence-integration.jar");
    return VfsUtilCore.toVirtualFileArray(result);
  }

  private static void appendIdeaLibrary(@NotNull String libDirPath,
                                        @NotNull List<VirtualFile> result,
                                        @NotNull @NonNls final String... forbidden) {
    Arrays.sort(forbidden);
    final String path = libDirPath + File.separator + LIB_DIR_NAME;
    final JarFileSystem jfs = JarFileSystem.getInstance();
    final File lib = new File(path);
    if (lib.isDirectory()) {
      File[] jars = lib.listFiles();
      if (jars != null) {
        for (File jar : jars) {
          @NonNls String name = jar.getName();
          if (jar.isFile() && Arrays.binarySearch(forbidden, name) < 0 && (name.endsWith(".jar") || name.endsWith(".zip"))) {
            VirtualFile file = jfs.findFileByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR);
            LOG.assertTrue(file != null, jar.getPath() + " not found");
            result.add(file);
          }
        }
      }
    }
  }


  @Override
  public boolean setupSdkPaths(@NotNull final Sdk sdk, @NotNull SdkModel sdkModel) {
    final Sandbox additionalData = (Sandbox)sdk.getSdkAdditionalData();
    if (additionalData != null) {
      additionalData.cleanupWatchedRoots();
    }

    SdkModificator sdkModificator = sdk.getSdkModificator();

    boolean result = setupSdkPaths(sdk, sdkModificator, sdkModel);

    if (result && sdkModificator.getSdkAdditionalData() == null) {
      List<String> javaSdks = new ArrayList<>();
      Sdk[] sdks = sdkModel.getSdks();
      for (Sdk jdk : sdks) {
        if (isValidInternalJdk(sdk, jdk)) {
          javaSdks.add(jdk.getName());
        }
      }
      if (javaSdks.isEmpty()) {
        JavaSdkVersion requiredVersion = getRequiredJdkVersion(sdk);
        if (requiredVersion != null) {
          Messages.showErrorDialog(DevKitBundle.message("no.java.sdk.for.idea.sdk.found", requiredVersion), "No Java SDK Found");
        }
        else {
          Messages.showErrorDialog(DevKitBundle.message("no.idea.sdk.version.found"), "No Java SDK Found");
        }
        return false;
      }

      int choice = Messages.showChooseDialog(
        "Select Java SDK to be used for " + DevKitBundle.message("sdk.title"),
        "Select Internal Java Platform",
        ArrayUtil.toStringArray(javaSdks), javaSdks.get(0), Messages.getQuestionIcon());
      if (choice != -1) {
        String name = javaSdks.get(choice);
        Sdk internalJava = ObjectUtils.assertNotNull(sdkModel.findSdk(name));
        //roots from internal jre
        setInternalJdk(sdk, sdkModificator, internalJava);
      }
      else {
        result = false;
      }
    }

    sdkModificator.commitChanges();
    return result;
  }

  private static void setInternalJdk(@NotNull Sdk sdk, @NotNull SdkModificator sdkModificator, @NotNull Sdk internalJava) {
    addClasses(sdkModificator, internalJava);
    addDocs(sdkModificator, internalJava);
    addSources(sdkModificator, internalJava);

    sdkModificator.setSdkAdditionalData(new Sandbox(getDefaultSandbox(), internalJava, sdk));
    sdkModificator.setVersionString(internalJava.getVersionString());
  }

  static boolean isValidInternalJdk(@NotNull Sdk ideaSdk, @NotNull Sdk sdk) {
    SdkTypeId sdkType = sdk.getSdkType();
    if (sdkType instanceof JavaSdk) {
      JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
      JavaSdkVersion requiredVersion = getRequiredJdkVersion(ideaSdk);
      if (version != null && requiredVersion != null) {
        return version.isAtLeast(requiredVersion);
      }
    }
    return false;
  }

  @Nullable
  private static JavaSdkVersion getRequiredJdkVersion(Sdk ideaSdk) {
    if (isFromIDEAProject(ideaSdk.getHomePath())) return JavaSdkVersion.JDK_1_8;
    File apiJar = getPlatformApiJar(ideaSdk.getHomePath());
    int classFileVersion = apiJar == null ? -1 : getIdeaClassFileVersion(apiJar);
    LanguageLevel languageLevel = classFileVersion <= 0? null : ClsParsingUtil.getLanguageLevelByVersion(classFileVersion);
    return languageLevel != null ? JavaSdkVersion.fromLanguageLevel(languageLevel) : null;
  }

  private static int getIdeaClassFileVersion(File apiJar) {
    try {
      try (ZipFile zipFile = new ZipFile(apiJar)) {
        ZipEntry entry = zipFile.getEntry(ApplicationStarter.class.getName().replace('.', '/') + ".class");
        if (entry != null) {
          try (DataInputStream stream = new DataInputStream(zipFile.getInputStream(entry))) {
            if (stream.skip(6) == 6) {
              return stream.readUnsignedShort();
            }
          }
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }

    return -1;
  }

  private static boolean setupSdkPaths(Sdk sdk, SdkModificator sdkModificator, SdkModel sdkModel) {
    String sdkHome = ObjectUtils.notNull(sdk.getHomePath());
    if (isFromIDEAProject(sdkHome)) {
      try {
        ProgressManager.getInstance().runProcessWithProgressSynchronously((ThrowableComputable<Void, IOException>)() -> {
          setupSdkPathsFromIDEAProject(sdk, sdkModificator, sdkModel);
          return null;
        }, "Scanning for Roots", true, null);
      }
      catch (ProcessCanceledException e) {
        return false;
      }
      catch (IOException e) {
        LOG.warn(e);
        Messages.showErrorDialog(e.toString(), DevKitBundle.message("sdk.title"));
        return false;
      }
    }
    else  {
      VirtualFile[] ideaLib = getIdeaLibrary(sdkHome);
      for (VirtualFile aIdeaLib : ideaLib) {
        sdkModificator.addRoot(aIdeaLib, OrderRootType.CLASSES);
      }
      addSources(new File(sdkHome), sdkModificator);
    }
    return true;
  }

  private static void setupSdkPathsFromIDEAProject(Sdk sdk, SdkModificator sdkModificator, SdkModel sdkModel) throws IOException {
    ProgressIndicator indicator = ObjectUtils.assertNotNull(ProgressManager.getInstance().getProgressIndicator());
    String sdkHome = ObjectUtils.notNull(sdk.getHomePath());
    JpsModel model = JpsSerializationManager.getInstance().loadModel(sdkHome, PathManager.getOptionsPath());
    JpsSdkReference<JpsDummyElement> sdkRef = model.getProject().getSdkReferencesTable().getSdkReference(JpsJavaSdkType.INSTANCE);
    String sdkName = sdkRef == null ? null : sdkRef.getSdkName();
    Sdk internalJava = sdkModel.findSdk(sdkName);
    if (internalJava != null && isValidInternalJdk(sdk, internalJava)) {
      setInternalJdk(sdk, sdkModificator, internalJava);
    }
    Set<VirtualFile> addedRoots = ContainerUtil.newTroveSet();
    VirtualFileManager vfsManager = VirtualFileManager.getInstance();
    JpsJavaExtensionService javaService = JpsJavaExtensionService.getInstance();
    boolean isUltimate = vfsManager.findFileByUrl(VfsUtilCore.pathToUrl(sdkHome + "/ultimate/ultimate-resources")) != null;
    Set<String> suppressedModules = ContainerUtil.newTroveSet("jps-plugin-system");
    Set<String> ultimateModules = ContainerUtil.newTroveSet(
      "platform-ultimate", "ultimate-resources", "ultimate-verifier",
      "diagram-api", "diagram-impl", "uml-plugin");
    List<JpsModule> modules = JBIterable.from(model.getProject().getModules())
      .filter(o -> {
        if (suppressedModules.contains(o.getName())) return false;
        if (o.getName().endsWith("-ide")) return false;
        String contentUrl = ContainerUtil.getFirstItem(o.getContentRootsList().getUrls());
        if (contentUrl == null) return true;
        // add only community modules/plugins to avoid EP duplicates & minor IDE conflicts
        return !isUltimate ||
               contentUrl.contains("/community/") && !contentUrl.contains("/community/python") ||
               ultimateModules.contains(o.getName());
      })
      .toList();
    indicator.setIndeterminate(false);
    double delta = 1 / (2 * Math.max(0.5, modules.size()));
    for (JpsModule o : modules) {
      indicator.setFraction(indicator.getFraction() + delta);
      for (JpsDependencyElement dep : o.getDependenciesList().getDependencies()) {
        ProgressManager.checkCanceled();
        JpsLibrary library = dep instanceof JpsLibraryDependency ? ((JpsLibraryDependency)dep).getLibrary() : null;
        if (library == null) continue;

        // do not check extension.getScope(), plugin projects need tests too
        //JpsLibraryType<?> libraryType = library == null ? null : library.getType();
        //if (!(libraryType instanceof JpsJavaLibraryType)) continue;
        //JpsJavaDependencyExtension extension = javaService.getDependencyExtension(dep);
        //if (extension == null) continue;

        for (JpsLibraryRoot jps : library.getRoots(JpsOrderRootType.COMPILED)) {
          VirtualFile root = vfsManager.findFileByUrl(jps.getUrl());
          if (root == null || !addedRoots.add(root)) continue;
          sdkModificator.addRoot(root, OrderRootType.CLASSES);
        }
        for (JpsLibraryRoot jps : library.getRoots(JpsOrderRootType.SOURCES)) {
          VirtualFile root = vfsManager.findFileByUrl(jps.getUrl());
          if (root == null || !addedRoots.add(root)) continue;
          sdkModificator.addRoot(root, OrderRootType.SOURCES);
        }
      }
    }
    for (JpsModule o : modules) {
      indicator.setFraction(indicator.getFraction() + delta);
      String outputUrl = javaService.getOutputUrl(o, false);
      VirtualFile outputRoot = outputUrl == null ? null : vfsManager.findFileByUrl(outputUrl);
      if (outputRoot == null) continue;
      sdkModificator.addRoot(outputRoot, OrderRootType.CLASSES);
      for (JpsModuleSourceRoot jps : o.getSourceRoots()) {
        ProgressManager.checkCanceled();
        VirtualFile root = vfsManager.findFileByUrl(jps.getUrl());
        if (root == null || !addedRoots.add(root)) continue;
        sdkModificator.addRoot(root, OrderRootType.SOURCES);
      }
    }
    indicator.setFraction(1.0);
  }

  static String getDefaultSandbox() {
    @NonNls String defaultSandbox = "";
    try {
      defaultSandbox = new File(PathManager.getSystemPath()).getCanonicalPath() + File.separator + "plugins-sandbox";
    }
    catch (IOException e) {
      //can't be on running instance
    }
    return defaultSandbox;
  }

  private static void addSources(File file, SdkModificator sdkModificator) {
    final File src = new File(new File(file, LIB_DIR_NAME), SRC_DIR_NAME);
    if (!src.exists()) return;
    File[] srcs = src.listFiles(pathname -> {
      @NonNls final String path = pathname.getPath();
      //noinspection SimplifiableIfStatement
      if (path.contains("generics")) return false;
      return path.endsWith(".jar") || path.endsWith(".zip");
    });
    for (int i = 0; srcs != null && i < srcs.length; i++) {
      File jarFile = srcs[i];
      if (jarFile.exists()) {
        JarFileSystem jarFileSystem = JarFileSystem.getInstance();
        String path = jarFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
        jarFileSystem.setNoCopyJarForPath(path);
        VirtualFile vFile = jarFileSystem.findFileByPath(path);
        sdkModificator.addRoot(vFile, OrderRootType.SOURCES);
      }
    }
  }

  private static void addClasses(@NotNull SdkModificator sdkModificator, @NotNull Sdk javaSdk) {
    addOrderEntries(OrderRootType.CLASSES, javaSdk, sdkModificator);
  }

  private static void addDocs(@NotNull SdkModificator sdkModificator, @NotNull Sdk javaSdk) {
    if (!addOrderEntries(JavadocOrderRootType.getInstance(), javaSdk, sdkModificator) && SystemInfo.isMac) {
      Sdk[] jdks = ProjectJdkTable.getInstance().getAllJdks();
      for (Sdk jdk : jdks) {
        if (jdk.getSdkType() instanceof JavaSdk) {
          addOrderEntries(JavadocOrderRootType.getInstance(), jdk, sdkModificator);
          break;
        }
      }
    }
  }

  private static void addSources(SdkModificator sdkModificator, final Sdk javaSdk) {
    if (javaSdk != null) {
      if (!addOrderEntries(OrderRootType.SOURCES, javaSdk, sdkModificator)){
        if (SystemInfo.isMac) {
          Sdk[] jdks = ProjectJdkTable.getInstance().getAllJdks();
          for (Sdk jdk : jdks) {
            if (jdk.getSdkType() instanceof JavaSdk) {
              addOrderEntries(OrderRootType.SOURCES, jdk, sdkModificator);
              break;
            }
          }
        }
        else {
          String homePath = javaSdk.getHomePath();
          if (homePath == null) return;
          final File jdkHome = new File(homePath).getParentFile();
          @NonNls final String srcZip = "src.zip";
          final File jarFile = new File(jdkHome, srcZip);
          if (jarFile.exists()){
            JarFileSystem jarFileSystem = JarFileSystem.getInstance();
            String path = jarFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
            jarFileSystem.setNoCopyJarForPath(path);
            sdkModificator.addRoot(jarFileSystem.findFileByPath(path), OrderRootType.SOURCES);
          }
        }
      }
    }
  }

  private static boolean addOrderEntries(@NotNull OrderRootType orderRootType, @NotNull Sdk sdk, @NotNull SdkModificator toModificator){
    boolean wasSmthAdded = false;
    final String[] entries = sdk.getRootProvider().getUrls(orderRootType);
    for (String entry : entries) {
      VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(entry);
      if (virtualFile != null) {
        toModificator.addRoot(virtualFile, orderRootType);
        wasSmthAdded = true;
      }
    }
    return wasSmthAdded;
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull final SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    return new IdeaJdkConfigurable(sdkModel, sdkModificator);
  }

  @Override
  @Nullable
  public String getBinPath(@NotNull Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getBinPath(internalJavaSdk);
  }

  @Override
  @Nullable
  public String getToolsPath(@NotNull Sdk sdk) {
    final Sdk jdk = getInternalJavaSdk(sdk);
    if (jdk != null && jdk.getVersionString() != null){
      return JavaSdk.getInstance().getToolsPath(jdk);
    }
    return null;
  }

  @Override
  @Nullable
  public String getVMExecutablePath(@NotNull Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getVMExecutablePath(internalJavaSdk);
  }

  @Override
  public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) {
    if (additionalData instanceof Sandbox) {
      try {
        ((Sandbox)additionalData).writeExternal(additional);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public SdkAdditionalData loadAdditionalData(@NotNull Sdk sdk, Element additional) {
    Sandbox sandbox = new Sandbox(sdk);
    try {
      sandbox.readExternal(additional);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
    return sandbox;
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return DevKitBundle.message("sdk.title");
  }

  @Nullable
  public static Sdk findIdeaJdk(@Nullable Sdk jdk) {
    if (jdk == null) return null;
    if (jdk.getSdkType() instanceof IdeaJdk) return jdk;
    return null;
  }

  public static SdkType getInstance() {
    return SdkType.findInstance(IdeaJdk.class);
  }

  @Override
  public boolean isRootTypeApplicable(@NotNull OrderRootType type) {
    return type == OrderRootType.CLASSES ||
           type == OrderRootType.SOURCES ||
           type == JavadocOrderRootType.getInstance() ||
           type == AnnotationOrderRootType.getInstance();
  }

  @Override
  public String getDefaultDocumentationUrl(@NotNull final Sdk sdk) {
    return JavaSdk.getInstance().getDefaultDocumentationUrl(sdk);
  }
}
