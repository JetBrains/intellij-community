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
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaDependentSdkType;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.compiled.ClsParsingUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.JBIterable;
import icons.DevkitIcons;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

/**
 * @author anna
 * @since Nov 22, 2004
 */
public class IdeaJdk extends JavaDependentSdkType implements JavaSdkType {
  private static final Icon ADD_SDK = DevkitIcons.Add_sdk;
  private static final Icon SDK_CLOSED = DevkitIcons.Sdk_closed;

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.projectRoots.IdeaJdk");
  @NonNls private static final String LIB_DIR_NAME = "lib";
  @NonNls private static final String SRC_DIR_NAME = "src";
  @NonNls private static final String PLUGINS_DIR = "plugins";
  public static final String OUT_CLASSES = "/out/classes/production";

  public IdeaJdk() {
    super("IDEA JDK");
  }

  public Icon getIcon() {
    return SDK_CLOSED;
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "reference.project.structure.sdk.idea";
  }

  @NotNull
  public Icon getIconForAddAction() {
    return ADD_SDK;
  }

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

  public boolean isValidSdkHome(String path) {
    if (isFromIDEAProject(path)) {
      return true;
    }
    File home = new File(path);
    if (!home.exists()) {
      return false;
    }
    if (getBuildNumber(path) == null || getOpenApiJar(path) == null) {
      return false;
    }
    return true;
  }

  @Nullable
  private static File getOpenApiJar(String home) {
    @NonNls final String openapiJar = "openapi.jar";
    @NonNls final String platformApiJar = "platform-api.jar";
    final File libDir = new File(home, LIB_DIR_NAME);
    File f = new File(libDir, openapiJar);
    if (f.exists()) return f;
    f = new File(libDir, platformApiJar);
    if (f.exists()) return f;
    return null;
  }

  public static boolean isFromIDEAProject(String path) {
    File ultimate = new File(path, "idea.iml");
    File community = new File(path, "community-main.iml");
    return ultimate.exists() && ultimate.isFile() ||
           community.exists() && community.isFile();
  }

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
    String plugins = home + File.separator + PLUGINS_DIR + File.separator;
    ArrayList<VirtualFile> result = new ArrayList<>();
    appendIdeaLibrary(home, result, "junit.jar");
    appendIdeaLibrary(plugins + "JavaEE", result, "javaee-impl.jar", "jpa-console.jar");
    appendIdeaLibrary(plugins + "PersistenceSupport", result, "persistence-impl.jar");
    appendIdeaLibrary(plugins + "DatabaseTools", result, "database-impl.jar", "jdbc-console.jar");
    appendIdeaLibrary(plugins + "css", result, "css.jar");
    appendIdeaLibrary(plugins + "uml", result, "uml-support.jar");
    appendIdeaLibrary(plugins + "Spring", result,
                      "spring.jar", "spring-el.jar", "spring-jsf.jar", "spring-persistence-integration.jar");
    return VfsUtilCore.toVirtualFileArray(result);
  }

  private static void appendIdeaLibrary(final String libDirPath,
                                        final ArrayList<VirtualFile> result,
                                        @NonNls final String... forbidden) {
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


  public boolean setupSdkPaths(@NotNull final Sdk sdk, @NotNull SdkModel sdkModel) {
    final Sandbox additionalData = (Sandbox)sdk.getSdkAdditionalData();
    if (additionalData != null) {
      additionalData.cleanupWatchedRoots();
    }

    final SdkModificator sdkModificator = sdk.getSdkModificator();

    final List<String> javaSdks = new ArrayList<>();
    final Sdk[] sdks = sdkModel.getSdks();
    for (Sdk jdk : sdks) {
      if (isValidInternalJdk(sdk, jdk)) {
        javaSdks.add(jdk.getName());
      }
    }
    if (javaSdks.isEmpty()){
      JavaSdkVersion requiredVersion = getRequiredJdkVersion(sdk);
      if (requiredVersion != null) {
        Messages.showErrorDialog(DevKitBundle.message("no.java.sdk.for.idea.sdk.found", requiredVersion), "No Java SDK Found");
      }
      else {
        Messages.showErrorDialog(DevKitBundle.message("no.idea.sdk.version.found"), "No Java SDK Found");
      }
      return false;
    }

    final int choice = Messages
      .showChooseDialog("Select Java SDK to be used for " + DevKitBundle.message("sdk.title"),
                        "Select Internal Java Platform",
                        ArrayUtil.toStringArray(javaSdks), javaSdks.get(0),
                        Messages.getQuestionIcon());

    if (choice != -1) {
      final String name = javaSdks.get(choice);
      final Sdk jdk = sdkModel.findSdk(name);
      LOG.assertTrue(jdk != null);
      setupSdkPaths(sdkModificator, sdk.getHomePath(), jdk);
      sdkModificator.setSdkAdditionalData(new Sandbox(getDefaultSandbox(), jdk, sdk));
      sdkModificator.setVersionString(jdk.getVersionString());
      sdkModificator.commitChanges();
      return true;
    }
    return false;
  }

  public static boolean isValidInternalJdk(Sdk ideaSdk, Sdk sdk) {
    final SdkTypeId sdkType = sdk.getSdkType();
    if (sdkType instanceof JavaSdk) {
      final JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
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
    File apiJar = getOpenApiJar(ideaSdk.getHomePath());
    int classFileVersion = apiJar == null ? -1 : getIdeaClassFileVersion(apiJar);
    LanguageLevel languageLevel = classFileVersion <= 0? null : ClsParsingUtil.getLanguageLevelByVersion(classFileVersion);
    return languageLevel != null ? JavaSdkVersion.fromLanguageLevel(languageLevel) : null;
  }

  private static int getIdeaClassFileVersion(File apiJar) {
    try {
      ZipFile zipFile = new ZipFile(apiJar);
      try {
        ZipEntry entry = zipFile.getEntry(ApplicationStarter.class.getName().replace('.', '/') + ".class");
        if (entry != null) {
          DataInputStream stream = new DataInputStream(zipFile.getInputStream(entry));
          try {
            if (stream.skip(6) == 6) {
              return stream.readUnsignedShort();
            }
          }
          finally {
            stream.close();
          }
        }
      }
      finally {
        zipFile.close();
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }

    return -1;
  }

  public static void setupSdkPaths(final SdkModificator sdkModificator, final String sdkHome, final Sdk internalJava) {
    //roots from internal jre
    addClasses(sdkModificator, internalJava);
    addDocs(sdkModificator, internalJava);
    addSources(sdkModificator, internalJava);
    //roots for openapi and other libs
    if (isFromIDEAProject(sdkHome)) {
      JarFileSystem jarFileSystem = JarFileSystem.getInstance();
      for (String prefix : Arrays.asList("community", "")) {
        for (String path : Arrays.asList("lib", "build/kotlinc/lib/kotlin-runtime.jar", "xml/relaxng/lib")) {
          File libDir = new File(sdkHome, FileUtil.toSystemDependentName(prefix + "/" + path));
          if (!libDir.exists()) continue;
          for (File file : JBIterable.of(libDir.listFiles()).append(libDir)) {
            if (file.isDirectory()) continue;
            if (!StringUtil.endsWithIgnoreCase(file.getName(), ".jar")) continue;
            String jarPath = FileUtil.toSystemIndependentName(file.getPath()) + JarFileSystem.JAR_SEPARATOR;
            VirtualFile jar = jarFileSystem.findFileByPath(jarPath);
            if (jar == null) continue;
            sdkModificator.addRoot(jar, OrderRootType.CLASSES);
          }
        }
      }
      LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
      VirtualFile out = localFileSystem.refreshAndFindFileByPath(sdkHome + OUT_CLASSES);
      if (out != null) {
        List<String> suppressed = Arrays.asList("jps-plugin-system");
        for (VirtualFile dir : JBIterable.of(out.findChild("resources")).append(out.getChildren())) {
          if (!dir.isDirectory()) continue;
          String name = dir.getName();
          if (name.endsWith("-ide") || suppressed.contains(name)) continue;
          if (!name.equals("ultimate-resources") &&
              FileUtil.exists(toSystemDependentName(dir.getPath() + "/META-INF/plugin.xml"))) continue;
          sdkModificator.addRoot(dir, OrderRootType.CLASSES);
        }
      }
    }
    else  {
      VirtualFile[] ideaLib = getIdeaLibrary(sdkHome);
      for (VirtualFile aIdeaLib : ideaLib) {
        sdkModificator.addRoot(aIdeaLib, OrderRootType.CLASSES);
      }
      addSources(new File(sdkHome), sdkModificator);
    }
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

  private static void addClasses(SdkModificator sdkModificator, final Sdk javaSdk) {
    addOrderEntries(OrderRootType.CLASSES, javaSdk, sdkModificator);
  }

  private static void addDocs(SdkModificator sdkModificator, final Sdk javaSdk) {
    if (!addOrderEntries(JavadocOrderRootType.getInstance(), javaSdk, sdkModificator) &&
        SystemInfo.isMac){
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

  private static boolean addOrderEntries(OrderRootType orderRootType, Sdk sdk, SdkModificator toModificator){
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

  public AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull final SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    return new IdeaJdkConfigurable(sdkModel, sdkModificator);
  }

  @Nullable
  public String getBinPath(@NotNull Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getBinPath(internalJavaSdk);
  }

  @Nullable
  public String getToolsPath(@NotNull Sdk sdk) {
    final Sdk jdk = getInternalJavaSdk(sdk);
    if (jdk != null && jdk.getVersionString() != null){
      return JavaSdk.getInstance().getToolsPath(jdk);
    }
    return null;
  }

  @Nullable
  public String getVMExecutablePath(@NotNull Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getVMExecutablePath(internalJavaSdk);
  }

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

  public String getDefaultDocumentationUrl(final @NotNull Sdk sdk) {
    return JavaSdk.getInstance().getDefaultDocumentationUrl(sdk);
  }
}
