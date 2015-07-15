package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.platform.loader.PlatformLoader;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderCreatorImpl implements SceneBuilderCreator {
  private final SceneBuilderInfo myInfo;
  private final ClassLoader myClassLoader;

  public SceneBuilderCreatorImpl(SceneBuilderInfo info) throws Exception {
    myInfo = info;
    myClassLoader = createSceneLoader(info);
  }

  @Override
  public State getState() {
    return State.OK;
  }

  @Override
  public SceneBuilder create(URL url, EditorCallback editorCallback) throws Exception {
    Class<?> wrapperClass = Class.forName("org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderKitWrapper", false, myClassLoader);
    return (SceneBuilder)wrapperClass.getMethod("create", URL.class, EditorCallback.class).invoke(null, url, editorCallback);
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof SceneBuilderCreatorImpl) {
      SceneBuilderCreatorImpl impl = (SceneBuilderCreatorImpl)object;
      return getState() == impl.getState() && myInfo.equals(impl.myInfo);
    }
    return false;
  }

  private static ClassLoader createSceneLoader(SceneBuilderInfo info) throws Exception {
    List<URL> urls = new ArrayList<URL>();

    File[] files = new File(info.libPath).listFiles();
    if (files == null) {
      throw new Exception(info.libPath + " wrong path");
    }

    for (File libFile : files) {
      if (libFile.isFile() && libFile.getName().endsWith(".jar")) {
        if (libFile.getName().equalsIgnoreCase("SceneBuilderApp.jar")) {
          JarFile appJar = new JarFile(libFile);
          String version = appJar.getManifest().getMainAttributes().getValue("Implementation-Version");
          appJar.close();

          if (version != null) {
            int index = version.indexOf(" ");
            if (index != -1) {
              version = version.substring(0, index);
            }
          }

          if (StringUtil.compareVersionNumbers(version, "2.0") < 0) {
            throw new Exception(info.path + " wrong version: " + version);
          }
        }

        urls.add(libFile.toURI().toURL());
      }
    }

    if (urls.isEmpty()) {
      throw new Exception(info.libPath + " no jar found");
    }

    RuntimeModuleId id = RuntimeModuleId.moduleResource("javaFX", "embedder.jar");
    for (String path : PlatformLoader.getInstance().getRepository().getModuleRootPaths(id)) {
      urls.add(new File(path).toURI().toURL());
    }

    final String rtPath = PathUtil.getJarPathForClass(String.class);
    final File javaFxJar = new File(new File(new File(rtPath).getParentFile(), "ext"), "jfxrt.jar");
    if (javaFxJar.isFile()) {
      urls.add(javaFxJar.toURI().toURL());
    }

    return new URLClassLoader(urls.toArray(new URL[urls.size()]), SceneBuilderCreatorImpl.class.getClassLoader());
  }
}