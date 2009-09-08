package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.SdkHomeConfigurable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
@State(
    name = "GantSettings",
    storages = {
      @Storage(id = "default", file = "$PROJECT_FILE$"),
      @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/gant_config.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class GantSettings implements PersistentStateComponent<SdkHomeConfigurable.SdkHomeSettings> {
  private SdkHomeConfigurable.SdkHomeSettings mySdkPath;
  private volatile VirtualFile mySdkHome;
  private volatile List<VirtualFile> myClassRoots;

  public SdkHomeConfigurable.SdkHomeSettings getState() {
    return mySdkPath;
  }

  public void loadState(SdkHomeConfigurable.SdkHomeSettings state) {
    mySdkPath = state;
    myClassRoots = null;
    mySdkHome = null;
  }
  
  private synchronized void calculateRoots() {
    if (myClassRoots != null) {
      return;
    }
    mySdkHome = calcHome(mySdkPath);
    myClassRoots = calcRoots(mySdkHome);
  }

  @Nullable 
  private static VirtualFile calcHome(final SdkHomeConfigurable.SdkHomeSettings state) {
    if (state == null) {
      return null;
    }

    @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"}) final String sdk_home = state.SDK_HOME;
    if (StringUtil.isEmpty(sdk_home)) {
      return null;
    }

    return LocalFileSystem.getInstance().findFileByPath(sdk_home);
  }

  @Nullable
  public VirtualFile getSdkHome() {
    if (myClassRoots == null) {
      calculateRoots();
    }
    return mySdkHome;
  }

  public List<VirtualFile> getClassRoots() {
    if (myClassRoots == null) {
      calculateRoots();
    }
    return myClassRoots;
  }

  private static List<VirtualFile> calcRoots(@Nullable VirtualFile home) {
    if (home == null) {
      return Collections.emptyList();
    }

    final VirtualFile lib = home.findChild("lib");
    if (lib == null) {
      return Collections.emptyList();
    }

    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile file : lib.getChildren()) {
      if ("jar".equals(file.getExtension())) {
        ContainerUtil.addIfNotNull(JarFileSystem.getInstance().getJarRootForLocalFile(file), result);
      }
    }
    return result;
  }

  public static GantSettings getInstance(Project project) {
    return ServiceManager.getService(project, GantSettings.class);
  }
}
