/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: Nov 22, 2004
 */
public class IdeaJdk extends SdkType implements ApplicationComponent {
  private static final Icon ADD_SDK = IconLoader.getIcon("/add_sdk.png");
  private static final Icon SDK_OPEN = IconLoader.getIcon("/sdk_open.png");
  private static final Icon SDK_CLOSED = IconLoader.getIcon("/sdk_closed.png");

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.projectRoots.IdeaJdk");
  @NonNls private static final String JAVA_HOME_PROPERTY = "java.home";
  @NonNls private static final String LIB_DIR_NAME = "lib";
  @NonNls private static final String SRC_DIR_NAME = "src";
  @NonNls private static final String JRE_DIR_NAME = "jre";
  @NonNls private static final String PLUGINS_DIR = "plugins";
  @NonNls private static final String JAVAEE_DIR = "JavaEE";
  @NonNls private static final String JSF_DIR = "JSF";

  public IdeaJdk() {
    super("IDEA JDK");
  }

  public Icon getIcon() {
    return SDK_CLOSED;
  }

  public Icon getIconForExpandedTreeNode() {
    return SDK_OPEN;
  }

  public Icon getIconForAddAction() {
    return ADD_SDK;
  }

  public String suggestHomePath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/');
  }

  public boolean isValidSdkHome(String path) {
    if (isFromIDEAProject(path)) {
      return true;
    }
    File home = new File(path);
    if (!home.exists()) {
      return false;
    }
    @NonNls final String openapiJar = "openapi.jar";
    if (getBuildNumber(path) == null || !new File(new File(home, LIB_DIR_NAME), openapiJar).exists()) {
      return false;
    }
    return true;
  }

  public static boolean isFromIDEAProject(String path) {
    File home = new File(path);
    File[] openapiDir = home.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        @NonNls final String name = pathname.getName();
        if (name.equals("openapi") && pathname.isDirectory()) return true; //todo
        return false;
      }
    });
    return openapiDir != null && openapiDir.length != 0;
  }

  @Nullable
  public final String getVersionString(final Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk != null ? internalJavaSdk.getVersionString() : null;
  }

  @Nullable
  private static String getInternalToolsPath(final Sdk sdk){
    if (SystemInfo.isLinux || SystemInfo.isWindows) {
      final @NonNls String toolsJar = "tools.jar";
      final File tools = new File(new File(new File(sdk.getHomePath(), JRE_DIR_NAME), LIB_DIR_NAME), toolsJar);
      if (tools.exists()){
        return tools.getPath();
      }
    }

    final ProjectJdk jdk = getInternalJavaSdk(sdk);
    if (jdk != null && jdk.getVersionString() != null){
      return jdk.getToolsPath();
    }
    return null;
  }

  @Nullable
  private static String getInternalRtPath(final Sdk sdk) {
    if (SystemInfo.isLinux || SystemInfo.isWindows) {
      final @NonNls String rtJar = "rt.jar";
      final String oldJrePath = sdk.getHomePath() + File.separator + JRE_DIR_NAME + File.separator;
      final String pathSuffix = LIB_DIR_NAME + File.separator + rtJar;
      String rtPath = oldJrePath + pathSuffix;
      if (new File(rtPath).exists()) {
        return rtPath;
      }
      rtPath = oldJrePath + JRE_DIR_NAME + File.separator + pathSuffix;
      if (new File(rtPath).exists()) {
        return rtPath;
      }
    }
    final ProjectJdk jdk = getInternalJavaSdk(sdk);
    if (jdk != null && jdk.getVersionString() != null){
      return jdk.getRtLibraryPath();
    }
    return null;
  }

  @Nullable
  private static ProjectJdk getInternalJavaSdk(final Sdk sdk) {
    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (data instanceof Sandbox) {
      return (ProjectJdk)((Sandbox)data).getSdk();
    }
    return null;
  }

  public String suggestSdkName(String currentSdkName, String sdkHome) {
    @NonNls final String idea = "IDEA ";
    String buildNumber = getBuildNumber(sdkHome);
    return idea + (buildNumber != null ? buildNumber : "");
  }

  @Nullable
  private static String getBuildNumber(String ideaHome) {
    try {
      @NonNls final String buildTxt = "/build.txt";
      return new String(FileUtil.loadFileText(new File(ideaHome + buildTxt))).trim();
    }
    catch (IOException e) {
      return null;
    }
  }

  private static VirtualFile[] getIdeaLibrary(String home) {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    appendIdeaLibrary(home + File.separator + LIB_DIR_NAME, "idea.jar", result);
    appendIdeaLibrary(home + File.separator + PLUGINS_DIR + File.separator + JAVAEE_DIR + File.separator + LIB_DIR_NAME, "javaee-impl.jar",
                      result);
    appendIdeaLibrary(home + File.separator + PLUGINS_DIR + File.separator + JSF_DIR + File.separator + LIB_DIR_NAME, "jsf-impl.jar", result);
    return result.toArray(new VirtualFile[result.size()]);
  }

  private static void appendIdeaLibrary(final String path, @NonNls final String forbidden, final ArrayList<VirtualFile> result) {
    final JarFileSystem jfs = JarFileSystem.getInstance();
    final File lib = new File(path);
    if (lib.isDirectory()) {
      File[] jars = lib.listFiles();
      if (jars != null) {
        for (File jar : jars) {
          @NonNls String name = jar.getName();
          if (jar.isFile() && !name.equals(forbidden) && (name.endsWith(".jar") || name.endsWith(".zip"))) {
            result.add(jfs.findFileByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR));
          }
        }
      }
    }
  }


  public boolean setupSdkPaths(final Sdk sdk, SdkModel sdkModel) {
    final Sandbox additionalData = (Sandbox)sdk.getSdkAdditionalData();
    if (additionalData != null) {    
      additionalData.cleanupWatchedRoots();
    }

    final SdkModificator sdkModificator = sdk.getSdkModificator();

    final List<String> javaSdks = new ArrayList<String>();
    final Sdk[] sdks = sdkModel.getSdks();
    for (Sdk jdk : sdks) {
      if (jdk.getSdkType() instanceof JavaSdk) {
        javaSdks.add(jdk.getName());
      }
    }
    if (javaSdks.isEmpty()){
      Messages.showErrorDialog("Please, configure Java SDK to be used as IDEA internal platform", "No Java SDK found");
      return false;
    }

    final int choice = Messages
      .showChooseDialog("Select Java SDK to be used as IDEA internal platform",
                        "Select internal Java platform", javaSdks.toArray(new String[javaSdks.size()]), javaSdks.get(0), Messages.getQuestionIcon());

    if (choice != -1) {
      final String name = javaSdks.get(choice);
      final Sdk jdk = sdkModel.findSdk(name);
      LOG.assertTrue(jdk != null);
      setupSdkPaths(sdk, sdkModificator, sdk.getHomePath(), jdk);
      sdkModificator.setSdkAdditionalData(new Sandbox(getDefaultSandbox(), jdk));
      sdkModificator.setVersionString(jdk.getVersionString());
      sdkModificator.commitChanges();
      return true;
    }
    return false;
  }

  public static void setupSdkPaths(final Sdk sdk, final SdkModificator sdkModificator, final String sdkHome, final Sdk internalJava) {
    //roots from internal jre
    addClasses(sdkModificator, internalJava);
    addDocs(sdkModificator, internalJava);
    addSources(sdkModificator, internalJava);
    //roots for openapi and other libs
    if (!isFromIDEAProject(sdkHome)) {
      final VirtualFile[] ideaLib = getIdeaLibrary(sdkHome);
      if (ideaLib != null) {
        for (VirtualFile aIdeaLib : ideaLib) {
          sdkModificator.addRoot(aIdeaLib, ProjectRootType.CLASS);
        }
      }
      addSources(new File(sdkHome), sdkModificator);
      addDocs(new File(sdkHome), sdkModificator);
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
    File[] srcs = src.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        @NonNls final String path = pathname.getPath();
        //noinspection SimplifiableIfStatement
        if (path.indexOf("generics") > -1) return false;
        return path.endsWith(".jar") || path.endsWith(".zip");
      }
    });
    for (int i = 0; srcs != null && i < srcs.length; i++) {
      File jarFile = srcs[i];
      if (jarFile.exists()) {
        JarFileSystem jarFileSystem = JarFileSystem.getInstance();
        String path = jarFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
        jarFileSystem.setNoCopyJarForPath(path);
        VirtualFile vFile = jarFileSystem.findFileByPath(path);
        sdkModificator.addRoot(vFile, ProjectRootType.SOURCE);
      }
    }
  }

  private static void addDocs(File file, final SdkModificator sdkModificator) {
    @NonNls final String help = "help";
    @NonNls final String openapi = "openapi";
    final File docFile = new File(new File(file, help), openapi);
    if (docFile.exists() && docFile.isDirectory()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          sdkModificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(docFile), ProjectRootType.JAVADOC);}
        }
      );
      return;
    }
    @NonNls final String openapiHelpJar = "openapihelp.jar";
    File jarfile = new File(new File(file, help), openapiHelpJar);
    if (jarfile.exists()) {
      JarFileSystem jarFileSystem = JarFileSystem.getInstance();
      String path = jarfile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR + openapi;
      jarFileSystem.setNoCopyJarForPath(path);
      VirtualFile vFile = jarFileSystem.findFileByPath(path);
      sdkModificator.addRoot(vFile, ProjectRootType.JAVADOC);
    }
  }

  private static void addClasses(SdkModificator sdkModificator, final Sdk javaSdk) {
    addOrderEntries(OrderRootType.CLASSES, ProjectRootType.CLASS, javaSdk, sdkModificator);
  }

  private static void addDocs(SdkModificator sdkModificator, final Sdk javaSdk) {
    if (!addOrderEntries(OrderRootType.JAVADOC, ProjectRootType.JAVADOC, javaSdk, sdkModificator) &&
        SystemInfo.isMac){
      ProjectJdk [] jdks = ProjectJdkTable.getInstance().getAllJdks();
      for (ProjectJdk jdk : jdks) {
        if (jdk.getSdkType() instanceof JavaSdk) {
          addOrderEntries(OrderRootType.JAVADOC, ProjectRootType.JAVADOC, jdk, sdkModificator);
          break;
        }
      }
    }
  }

  private static void addSources(SdkModificator sdkModificator, final Sdk javaSdk) {
    if (javaSdk != null) {
      if (!addOrderEntries(OrderRootType.SOURCES, ProjectRootType.SOURCE, javaSdk, sdkModificator)){
        if (SystemInfo.isMac) {
          ProjectJdk [] jdks = ProjectJdkTable.getInstance().getAllJdks();
          for (ProjectJdk jdk : jdks) {
            if (jdk.getSdkType() instanceof JavaSdk) {
              addOrderEntries(OrderRootType.SOURCES, ProjectRootType.SOURCE, jdk, sdkModificator);
              break;
            }
          }
        }
        else {
          final File jdkHome = new File(javaSdk.getHomePath()).getParentFile();
          @NonNls final String srcZip = "src.zip";
          final File jarFile = new File(jdkHome, srcZip);
          if (jarFile.exists()){
            JarFileSystem jarFileSystem = JarFileSystem.getInstance();
            String path = jarFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
            jarFileSystem.setNoCopyJarForPath(path);
            sdkModificator.addRoot(jarFileSystem.findFileByPath(path), ProjectRootType.SOURCE);
          }
        }
      }
    }
  }

  private static boolean addOrderEntries(OrderRootType orderRootType, ProjectRootType projectRootType, Sdk sdk, SdkModificator toModificator){
    boolean wasSmthAdded = false;
    final String[] entries = sdk.getRootProvider().getUrls(orderRootType);
    for (String entry : entries) {
      VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(entry);
      toModificator.addRoot(virtualFile, projectRootType);
      wasSmthAdded = true;
    }
    return wasSmthAdded;
  }

  public AdditionalDataConfigurable createAdditionalDataConfigurable(final SdkModel sdkModel, SdkModificator sdkModificator) {
    final IdeaJdkConfigurable mobileSdkConfigurable = new IdeaJdkConfigurable(sdkModel, sdkModificator);
    sdkModel.addListener(new SdkModel.Listener() {
      public void sdkAdded(Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          mobileSdkConfigurable.addJavaSdk(sdk);
        }
      }

      public void beforeSdkRemove(Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          mobileSdkConfigurable.removeJavaSdk(sdk);
        }
      }

      public void sdkChanged(Sdk sdk, String previousName) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          mobileSdkConfigurable.updateJavaSdkList(sdk, previousName);
        }
      }

      public void sdkHomeSelected(final Sdk sdk, final String newSdkHome) {
        if (sdk.getSdkType() instanceof IdeaJdk) {
          mobileSdkConfigurable.updateRoots(newSdkHome);
        }
      }
    });

    return mobileSdkConfigurable;
  }

  @Nullable
  public String getBinPath(Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getBinPath(internalJavaSdk);
  }

  @Nullable
  public String getToolsPath(Sdk sdk) {
    return getInternalToolsPath(sdk);
  }

  @Nullable
  public String getVMExecutablePath(Sdk sdk) {
    final Sdk internalJavaSdk = getInternalJavaSdk(sdk);
    return internalJavaSdk == null ? null : JavaSdk.getInstance().getVMExecutablePath(internalJavaSdk);
  }

  @Nullable
  public String getRtLibraryPath(Sdk sdk) {
    return getInternalRtPath(sdk);
  }

  public void saveAdditionalData(SdkAdditionalData additionalData, Element additional) {
    if (additionalData instanceof Sandbox) {
      try {
        ((Sandbox)additionalData).writeExternal(additional);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
      }
    }
  }

  public SdkAdditionalData loadAdditionalData(Element additional) {
    Sandbox sandbox = new Sandbox();
    try {
      sandbox.readExternal(additional);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
    return sandbox;
  }

  public String getPresentableName() {
    return DevKitBundle.message("sdk.title");
  }

  @NotNull
  public String getComponentName() {
    return getName();
  }

  public void initComponent() {}

  public void disposeComponent() {}

  @Nullable
  public static ProjectJdk findIdeaJdk(@Nullable ProjectJdk jdk) {
    if (jdk == null) return null;

    while(true) {
      if (jdk.getSdkType() instanceof IdeaJdk) return jdk;
      final Sdk encapsulated = jdk.getSdkType().getEncapsulatedSdk(jdk);
      if (!(encapsulated instanceof ProjectJdk)) return null;
      jdk = (ProjectJdk)encapsulated;
    }
  }
}
