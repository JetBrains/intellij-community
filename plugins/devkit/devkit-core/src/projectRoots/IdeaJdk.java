// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.impl.compiled.ClsParsingUtil;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.run.ProductInfo;
import org.jetbrains.idea.devkit.run.ProductInfoKt;
import org.jetbrains.idea.devkit.util.PsiUtil;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class IdeaJdk extends JavaDependentSdkType implements JavaSdkType {
  private static final Logger LOG = Logger.getInstance(IdeaJdk.class);

  private static final String LIB_DIR_NAME = "lib";
  private static final String LIB_SRC_DIR_NAME = "lib/src";
  private static final String PLUGINS_DIR = "plugins";

  public IdeaJdk() {
    super("IDEA JDK");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Plugin;
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "reference.project.structure.sdk.idea";
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
  public boolean isValidSdkHome(@NotNull String path) {
    if (PsiUtil.isPathToIntelliJIdeaSources(path)) {
      return true;
    }
    File home = new File(path);
    return home.exists() && getBuildNumber(path) != null && getPlatformApiJar(path) != null;
  }

  private static @Nullable Path getPlatformApiJar(String home) {
    Path libDir = Path.of(home, LIB_DIR_NAME);
    // in 173.* and earlier builds all IDEs included platform modules into openapi.jar
    // (see org.jetbrains.intellij.build.ProductModulesLayout.platformApiModules)
    // DO NOT change order of this list, first match must return JAR containing marker class for getRequiredJdkVersion()
    for (String name : List.of("app-client.jar", "app.jar", "platform-api.jar", "openapi.jar")) {
      Path result = libDir.resolve(name);
      if (Files.exists(result)) {
        return result;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public String getVersionString(@NotNull final Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk != null ? internalJavaSdk.getVersionString() : null;
  }

  @Nullable
  public static Sdk getInternalJavaSdk(final Sdk sdk) {
    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (data instanceof Sandbox) {
      return ((Sandbox)data).getJavaSdk();
    }
    return null;
  }

  @NotNull
  @Override
  public String suggestSdkName(@Nullable String currentSdkName, @NotNull String sdkHome) {
    if (PsiUtil.isPathToIntelliJIdeaSources(sdkHome)) return "Local IDEA [" + sdkHome + "]"; //NON-NLS
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
    ProductInfo productInfo = ProductInfoKt.loadProductInfo(home);
    if (productInfo != null) {
      final JarFileSystem jfs = JarFileSystem.getInstance();
      for (String productModuleJarPath : productInfo.getProductModuleJarPaths()) {
        VirtualFile vf = jfs.findFileByPath(home + File.separator + productModuleJarPath + JarFileSystem.JAR_SEPARATOR);
        LOG.assertTrue(vf != null, productModuleJarPath + " not found in " + home);
        result.add(vf);
      }
    }

    String plugins = home + File.separator + PLUGINS_DIR + File.separator;
    appendIdeaLibrary(plugins + "java", result);
    appendIdeaLibrary(plugins + "JavaEE", result, "javaee-impl.jar", "jpa-javax-console.jar", "jpa-jakarta-console.jar",
                      "jpa-console-common.jar");
    appendIdeaLibrary(plugins + "PersistenceSupport", result, "persistence-impl.jar");
    appendIdeaLibrary(plugins + "DatabaseTools", result, "grid.jar", "grid-core.jar", "database-impl.jar", "jdbc-console.jar");
    appendIdeaLibrary(plugins + "css", result, "css.jar");
    appendIdeaLibrary(plugins + "uml", result, "uml-support.jar");
    appendIdeaLibrary(plugins + "Spring", result,
                      "spring.jar", "spring-el.jar", "spring-jsf.jar", "spring-persistence-integration.jar");
    return VfsUtilCore.toVirtualFileArray(result);
  }

  private static void appendIdeaLibrary(@NonNls @NotNull String libDirPath,
                                        @NotNull List<VirtualFile> result,
                                        @NonNls final String @NotNull ... forbidden) {
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
          Messages.showErrorDialog(DevKitBundle.message("sdk.no.java.sdk.for.idea.sdk.found", requiredVersion),
                                   DevKitBundle.message("sdk.no.java.sdk.for.idea.sdk.found.title"));
        }
        else {
          Messages.showErrorDialog(DevKitBundle.message("sdk.no.idea.sdk.version.found"),
                                   DevKitBundle.message("sdk.no.java.sdk.for.idea.sdk.found.title"));
        }
        return false;
      }

      @NlsSafe String firstSdkName = javaSdks.get(0);
      Ref<Integer> choice = Ref.create();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        choice.set(Messages.showChooseDialog(
          DevKitBundle.message("sdk.select.java.sdk"),
          DevKitBundle.message("sdk.select.java.sdk.title"),
          ArrayUtilRt.toStringArray(javaSdks), firstSdkName, Messages.getQuestionIcon()));
      });
      if (choice.get() != -1) {
        String name = javaSdks.get(choice.get());
        Sdk internalJava = Objects.requireNonNull(sdkModel.findSdk(name));
        //roots from internal jre
        setInternalJdk(sdk, sdkModificator, internalJava);
      }
      else {
        result = false;
      }
    }

    ApplicationManager.getApplication().runWriteAction(() -> sdkModificator.commitChanges());
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

  private static @Nullable JavaSdkVersion getRequiredJdkVersion(Sdk ideaSdk) {
    if (PsiUtil.isPathToIntelliJIdeaSources(ideaSdk.getHomePath())) return JavaSdkVersion.JDK_1_8;
    Path apiJar = getPlatformApiJar(ideaSdk.getHomePath());
    return apiJar != null ? ClsParsingUtil.getJdkVersionByBytecode(getIdeaClassFileVersion(apiJar)) : null;
  }

  private static int getIdeaClassFileVersion(Path apiJar) {
    try {
      try (ZipFile zipFile = new ZipFile(apiJar.toFile())) {
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
    String sdkHome = Objects.requireNonNull(sdk.getHomePath());
    if (PsiUtil.isPathToIntelliJIdeaSources(sdkHome)) {
      try {
        ProgressManager.getInstance().runProcessWithProgressSynchronously((ThrowableComputable<Void, IOException>)() -> {
          setupSdkPathsFromIDEAProject(sdk, sdkModificator, sdkModel);
          return null;
        }, DevKitBundle.message("sdk.from.sources.scanning.roots"), true, null);
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
    else {
      VirtualFile[] ideaLib = getIdeaLibrary(sdkHome);
      for (VirtualFile aIdeaLib : ideaLib) {
        sdkModificator.addRoot(aIdeaLib, OrderRootType.CLASSES);
      }
      addSources(new File(sdkHome), sdkModificator);
    }
    return true;
  }

  private static void setupSdkPathsFromIDEAProject(Sdk sdk, SdkModificator sdkModificator, SdkModel sdkModel) throws IOException {
    ProgressIndicator indicator = Objects.requireNonNull(ProgressManager.getInstance().getProgressIndicator());
    String sdkHome = Objects.requireNonNull(sdk.getHomePath());
    JpsModel model = JpsSerializationManager.getInstance().loadModel(sdkHome, PathManager.getOptionsPath());
    JpsSdkReference<JpsDummyElement> sdkRef = model.getProject().getSdkReferencesTable().getSdkReference(JpsJavaSdkType.INSTANCE);
    Sdk internalJava = sdkRef == null ? null : sdkModel.findSdk(sdkRef.getSdkName());
    if (internalJava != null && isValidInternalJdk(sdk, internalJava)) {
      setInternalJdk(sdk, sdkModificator, internalJava);
    }

    Map<String, JpsModule> moduleByName =
      model.getProject().getModules().stream().collect(Collectors.toMap(JpsModule::getName, Function.identity()));
    @NonNls String[] mainModuleCandidates = {
      "intellij.idea.ultimate.main",
      "intellij.idea.community.main",
      "main",
      "community-main"
    };
    JpsModule mainModule = Arrays.stream(mainModuleCandidates).map(moduleByName::get).filter(Objects::nonNull).findFirst().orElse(null);
    if (mainModule == null) {
      LOG.error("Cannot find main module (" + Arrays.toString(mainModuleCandidates) + ") in IntelliJ IDEA sources at " + sdkHome);
      return;
    }

    Set<JpsModule> modules = new LinkedHashSet<>();
    JpsJavaExtensionService.dependencies(mainModule).recursively().processModules(modules::add);

    indicator.setIndeterminate(false);
    double delta = 1 / (2 * Math.max(0.5, modules.size()));
    JpsJavaExtensionService javaService = JpsJavaExtensionService.getInstance();
    VirtualFileManager vfsManager = VirtualFileManager.getInstance();
    Set<VirtualFile> addedRoots = new HashSet<>();
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
    File[] files = new File(file, LIB_SRC_DIR_NAME).listFiles();
    if (files != null) {
      JarFileSystem fs = JarFileSystem.getInstance();
      for (File child : files) {
        String path = child.getAbsolutePath();
        if (!path.contains("generics") && (path.endsWith(".jar") || path.endsWith(".zip"))) {
          VirtualFile vFile = fs.refreshAndFindFileByPath(path + JarFileSystem.JAR_SEPARATOR);
          if (vFile != null) {
            sdkModificator.addRoot(vFile, OrderRootType.SOURCES);
          }
        }
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
      if (!addOrderEntries(OrderRootType.SOURCES, javaSdk, sdkModificator)) {
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
          File jarFile = new File(new File(homePath).getParentFile(), "src.zip");
          if (jarFile.exists()) {
            JarFileSystem jarFileSystem = JarFileSystem.getInstance();
            String path = jarFile.getAbsolutePath();
            VirtualFile vFile = jarFileSystem.refreshAndFindFileByPath(path + JarFileSystem.JAR_SEPARATOR);
            if (vFile != null) {
              sdkModificator.addRoot(vFile, OrderRootType.SOURCES);
            }
          }
        }
      }
    }
  }

  private static boolean addOrderEntries(@NotNull OrderRootType orderRootType, @NotNull Sdk sdk, @NotNull SdkModificator toModificator) {
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
  public AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull final SdkModel sdkModel,
                                                                     @NotNull SdkModificator sdkModificator) {
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
    if (jdk != null && jdk.getVersionString() != null) {
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
  public SdkAdditionalData loadAdditionalData(@NotNull Sdk sdk, @NotNull Element additional) {
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
