package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Element;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;

/**
 * User: anna
 * Date: Nov 22, 2004
 */
public class IdeaJdk extends SdkType implements ApplicationComponent {
  public static final Icon ADD_SDK = IconLoader.getIcon("/add_sdk.png");
  public static final Icon SDK_OPEN = IconLoader.getIcon("/sdk_open.png");
  public static final Icon SDK_CLOSED = IconLoader.getIcon("/sdk_closed.png");

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit");

  private ProjectJdk myInternalJavaSdk;

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

  public boolean isValidSdkHome(String path) {
    if (isFromIDEAProject(path)) {
      return true;
    }
    File home = new File(path);
    if (!home.exists()) {
      return false;
    }
    if (getBuildNumber(path) == null || !new File(new File(home, "lib"), "openapi.jar").exists()) {
      return false;
    }
    return true;
  }

  public static boolean isFromIDEAProject(String path) {
    File home = new File(path);
    File[] openapiDir = home.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        if (pathname.getName().equals("openapi") && pathname.isDirectory()) return true; //todo
        return false;
      }
    });
    if (openapiDir == null || openapiDir.length == 0) {
      return false;
    }
    return true;
  }

  public final String getVersionString(final String sdkHome) {
    return getInternalJavaSdk(sdkHome).getVersionString();
  }

  private Sdk getInternalJavaSdk(final String sdkHome) {
    if (myInternalJavaSdk != null){
      return myInternalJavaSdk;
    }
    String jreHome;
    if (SystemInfo.isLinux || SystemInfo.isWindows) {
      jreHome = sdkHome + File.separator + "jre";
      if (!new File(jreHome).exists()) {
        jreHome = System.getProperty("java.home");
      }
    }
    else {
      jreHome = System.getProperty("java.home");
    }

    myInternalJavaSdk = JavaSdk.getInstance().createJdk("", jreHome);
    return myInternalJavaSdk;
  }

  public String suggestSdkName(String currentSdkName, String sdkHome) {
    return "IDEA " + (getBuildNumber(sdkHome) != null ? getBuildNumber(sdkHome) : "");
  }

  private String getBuildNumber(String ideaHome) {
    try {
      BufferedReader reader = new BufferedReader(new FileReader(ideaHome + "/build.txt"));
      return reader.readLine().trim();
    }
    catch (IOException e) {
    }

    return null;
  }

  private VirtualFile[] getIdeaLibrary(String home) {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    JarFileSystem jfs = JarFileSystem.getInstance();
    File lib = new File(home + "/lib");
    File[] jars = lib.listFiles();
    if (jars != null) {
      for (int i = 0; i < jars.length; i++) {
        File jar = jars[i];
        String name = jar.getName();
        if (jar.isFile() && !name.equals("idea.jar") && (name.endsWith(".jar") || name.endsWith(".zip"))) {
          result.add(jfs.findFileByPath(jar.getPath() + JarFileSystem.JAR_SEPARATOR));
        }

      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }


  public void setupSdkPaths(Sdk sdk) {
    final SdkModificator sdkModificator = sdk.getSdkModificator();
    final File home = new File(sdk.getHomePath());

    //roots from internal jre
    addClasses(sdkModificator);
    addDocs(sdkModificator);
    addSources(sdkModificator);

    //roots for openapi and other libs
    if (!isFromIDEAProject(sdk.getHomePath())) {
      final VirtualFile[] ideaLib = getIdeaLibrary(sdk.getHomePath());
      if (ideaLib != null) {
        for (int i = 0; i < ideaLib.length; i++) {
          sdkModificator.addRoot(ideaLib[i], ProjectRootType.CLASS);
        }
      }
      addSources(home, sdkModificator);
      addDocs(home, sdkModificator);
    }
    sdkModificator.commitChanges();
  }

  public static void addSources(File file, SdkModificator sdkModificator) {
    final File src = new File(new File(file, "lib"), "src");
    if (!src.exists()) return;
    File[] srcs = src.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        if (pathname.getPath().indexOf("generics") > -1) return false;
        if (pathname.getPath().endsWith(".jar") || pathname.getPath().endsWith(".zip")) return true;
        return false;
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

  public static void addDocs(File file, SdkModificator sdkModificator) {
    File docFile = new File(new File(file, "help"), "openapi");
    if (docFile.exists() && docFile.isDirectory()) {
      sdkModificator.addRoot(LocalFileSystem.getInstance().findFileByIoFile(docFile), ProjectRootType.JAVADOC);
      return;
    }
    File jarfile = new File(new File(file, "help"), "openapihelp.jar");
    if (jarfile.exists()) {
      JarFileSystem jarFileSystem = JarFileSystem.getInstance();
      String path = jarfile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR + "openapi";
      jarFileSystem.setNoCopyJarForPath(path);
      VirtualFile vFile = jarFileSystem.findFileByPath(path);
      sdkModificator.addRoot(vFile, ProjectRootType.JAVADOC);
    }
  }

  private void addClasses(SdkModificator sdkModificator) {
    addOrderEntries(OrderRootType.CLASSES, ProjectRootType.CLASS, getInternalJavaSdk(sdkModificator.getHomePath()), sdkModificator);
  }

  private void addDocs(SdkModificator sdkModificator) {
    if (!addOrderEntries(OrderRootType.JAVADOC, ProjectRootType.JAVADOC, getInternalJavaSdk(sdkModificator.getHomePath()), sdkModificator) &&
        SystemInfo.isMac){
      ProjectJdk [] jdks = ProjectJdkTable.getInstance().getAllJdks();
      for(int i = 0; i < jdks.length; i++){
        if (jdks[i].getSdkType() instanceof JavaSdk){
          addOrderEntries(OrderRootType.JAVADOC, ProjectRootType.JAVADOC, jdks[i], sdkModificator);
          break;
        }
      }
    }
  }

  private void addSources(SdkModificator sdkModificator) {
    if (!addOrderEntries(OrderRootType.SOURCES, ProjectRootType.SOURCE, getInternalJavaSdk(sdkModificator.getHomePath()), sdkModificator) &&
        SystemInfo.isMac){
      ProjectJdk [] jdks = ProjectJdkTable.getInstance().getAllJdks();
      for(int i = 0; i < jdks.length; i++){
        if (jdks[i].getSdkType() instanceof JavaSdk){
          addOrderEntries(OrderRootType.SOURCES, ProjectRootType.SOURCE, jdks[i], sdkModificator);
          break;
        }
      }
    }
  }

  private boolean addOrderEntries(OrderRootType orderRootType, ProjectRootType projectRootType, Sdk sdk, SdkModificator toModificator){
    boolean wasSmthAdded = false;
    final String[] entries = sdk.getRootProvider().getUrls(orderRootType);
    for (int i = 0; i < entries.length; i++) {
      VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(entries[i]);
      toModificator.addRoot(virtualFile, projectRootType);
      wasSmthAdded = true;
    }
    return wasSmthAdded;
  }

  public AdditionalDataConfigurable createAdditionalDataConfigurable(final SdkModel sdkModel, SdkModificator sdkModificator) {
    return new IdeaJdkConfigurable();
  }

  public String getBinPath(Sdk sdk) {
    return JavaSdk.getInstance().getBinPath(getInternalJavaSdk(sdk.getHomePath()));
  }

  public String getToolsPath(Sdk sdk) {
    return JavaSdk.getInstance().getToolsPath(getInternalJavaSdk(sdk.getHomePath()));
  }

  public String getVMExecutablePath(Sdk sdk) {
    return JavaSdk.getInstance().getVMExecutablePath(getInternalJavaSdk(sdk.getHomePath()));
  }

  public String getRtLibraryPath(Sdk sdk) {
    return JavaSdk.getInstance().getRtLibraryPath(getInternalJavaSdk(sdk.getHomePath()));
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
    return "IntelliJ IDEA SDK";
  }

  public String getComponentName() {
    return getName();
  }

  public void initComponent() {}

  public void disposeComponent() {}
}
