package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
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
  private static final String VM_EXE_NAME = "java";
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
    if (isFromIDEAProject(path)){
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
    return "1.3";   //todo from ProjectJdkUtil
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
    if (!isFromIDEAProject(sdk.getHomePath())) {
      final VirtualFile[] ideaLib = getIdeaLibrary(sdk.getHomePath());
      if (ideaLib != null) {
        for (int i = 0; i < ideaLib.length; i++) {
          sdkModificator.addRoot(ideaLib[i], ProjectRootType.CLASS);
        }
      }
    }
    addClasses(new File(new File(sdk.getHomePath()), "jre"), sdkModificator, JarFileSystem.getInstance());
    sdkModificator.commitChanges();
  }

  private static void addClasses(File file, SdkModificator sdkModificator, JarFileSystem jarFileSystem) {
    VirtualFile[] classes = findClasses(file, jarFileSystem);
    for (int i = 0; i < classes.length; i++) {
      VirtualFile virtualFile = classes[i];
      sdkModificator.addRoot(virtualFile, ProjectRootType.CLASS);
    }
  }

  private static VirtualFile[] findClasses(File file, JarFileSystem jarFileSystem) {
    FileFilter jarFileFilter = new FileFilter() {
      public boolean accept(File f) {
        if (f.isDirectory()) return false;
        if (f.getName().endsWith(".jar")) return true;
        return false;
      }
    };

    File[] jarDirs;
    if (SystemInfo.isMac && !ApplicationManager.getApplication().isUnitTestMode()) {
      File libFile = new File(file, "lib");
      File classesFile = new File(file, "../Classes");
      File libExtFile = new File(libFile, "ext");
      jarDirs = new File[]{libFile, classesFile, libExtFile};
    }
    else {
      File jreLibFile = new File(file, "lib");
      File jreLibExtFile = new File(jreLibFile, "ext");
      jarDirs = new File[]{jreLibFile, jreLibExtFile};
    }

    ArrayList<File> childrenList = new ArrayList<File>();
    for (int i = 0; i < jarDirs.length; i++) {
      File jarDir = jarDirs[i];
      if ((jarDir != null) && jarDir.isDirectory()) {
        File[] files = jarDir.listFiles(jarFileFilter);
        for (int j = 0; j < files.length; j++) {
          childrenList.add(files[j]);
        }
      }
    }

    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (int i = 0; i < childrenList.size(); i++) {
      File child = (File)childrenList.get(i);
      String path = child.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
      // todo ((JarFileSystemEx)jarFileSystem).setNoCopyJarForPath(path);
      VirtualFile vFile = jarFileSystem.findFileByPath(path);
      if (vFile != null) {
        result.add(vFile);
      }
    }

    File classesZipFile = new File(new File(file, "lib"), "classes.zip");
    if ((!classesZipFile.isDirectory()) && classesZipFile.exists()) {
      String path = classesZipFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
      //todo ((JarFileSystemEx)jarFileSystem).setNoCopyJarForPath(path);
      VirtualFile vFile = jarFileSystem.findFileByPath(path);
      if (vFile != null) {
        result.add(vFile);
      }
    }

    return (VirtualFile[])result.toArray(new VirtualFile[result.size()]);
  }

  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel) {
    return new IdeaJdkConfigurable();
  }

  public String getBinPath(Sdk sdk) {
    return getConvertedHomePath(sdk) + "jre" + File.separator + "bin";
  }

  public String getToolsPath(Sdk sdk) {
    final String versionString = sdk.getVersionString();
    final boolean isJdk1_x = versionString.indexOf("1.0") > -1 || versionString.indexOf("1.1") > -1; //todo check
    return getConvertedHomePath(sdk) + "jre" + File.separator + "lib" + File.separator + (isJdk1_x ? "classes.zip" : "tools.jar");
  }

  public String getVMExecutablePath(Sdk sdk) {
    if ("64".equals(System.getProperty("sun.arch.data.model"))) {
      return getBinPath(sdk) + File.separator + System.getProperty("os.arch") + File.separator + VM_EXE_NAME;
    }
    return getBinPath(sdk) + File.separator + VM_EXE_NAME;
  }

  public String getRtLibraryPath(Sdk sdk) {
    return getConvertedHomePath(sdk) + "jre" + File.separator + "lib" + File.separator + "rt.jar";
  }

  private String getConvertedHomePath(Sdk sdk) {
    String path = sdk.getHomePath().replace('/', File.separatorChar);
    if (!path.endsWith(File.separator)) {
      path = path + File.separator;
    }
    return path;
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
    return "IDEA Jdk";
  }

  public String getComponentName() {
    return getName();
  }

  public void initComponent() {}

  public void disposeComponent() {}
}
