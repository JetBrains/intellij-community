package org.jetbrains.android.uipreview;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public final class ProjectClassLoader extends ClassLoader {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.ProjectClassLoader");

  private final Module myModule;
  private URLClassLoader mJarClassLoader;
  private boolean mInsideJarClassLoader = false;

  public ProjectClassLoader(ClassLoader parentClassLoader, Module module) {
    super(parentClassLoader);
    myModule = module;
  }

  public static ClassLoader create(IAndroidTarget target, Module module) throws Exception {
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(module);
    AndroidTargetData targetData = androidPlatform.getSdkData().getTargetData(target);
    RenderServiceFactory factory = targetData.getRenderServiceFactory(module.getProject());
    return new ProjectClassLoader(factory.getLibrary().getClassLoader(), module);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    if (mInsideJarClassLoader) {
      throw new ClassNotFoundException(name);
    }

    try {
      final Class<?> aClass = loadClassFromModuleOrDependency(myModule, name, new HashSet<Module>());
      if (aClass != null) {
        return aClass;
      }
    }
    catch (UnsupportedClassVersionError e) {
      throw new IncompatibleClassFileFormatException(name);
    }

    throw new ClassNotFoundException(name);
  }

  @Nullable
  private Class<?> loadClassFromModuleOrDependency(Module module, String name, Set<Module> visited) {
    if (!visited.add(module)) {
      return null;
    }

    Class<?> aClass = loadClassFromModule(module, name);
    if (aClass != null) {
      return aClass;
    }

    aClass = loadClassFromJar(name);
    if (aClass != null) {
      return aClass;
    }

    for (Module depModule : ModuleRootManager.getInstance(module).getDependencies(false)) {
      aClass = loadClassFromModuleOrDependency(depModule, name, visited);
      if (aClass != null) {
        return aClass;
      }
    }
    return null;
  }

  @Nullable
  private Class<?> loadClassFromModule(Module module, String name) {
    final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
    if (extension == null) {
      return null;
    }

    final VirtualFile vOutFolder = extension.getCompilerOutputPath();
    if (vOutFolder == null) {
      return null;
    }

    final String[] segments = name.split("\\.");

    final File classFile = findClassFile(new File(vOutFolder.getPath()), segments, 0);

    if (classFile == null || !classFile.exists()) {
      return null;
    }

    final DataInputStream fis;
    try {
      fis = new DataInputStream(new FileInputStream(classFile));
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
      return null;
    }

    try {
      byte[] data = new byte[(int)classFile.length()];
      try {
        fis.readFully(data);
      }
      catch (IOException e) {
        data = null;
      }
      if (data != null) {
        final Class<?> aClass = defineClass(null, data, 0, data.length);
        if (aClass != null) {
          return aClass;
        }
      }
    }
    finally {
      try {
        fis.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  @Nullable
  private static File findClassFile(File parent, String[] segments, int index) {
    if (index == segments.length) {
      return null;
    }
    final File[] children = parent.listFiles();

    if (children == null || children.length == 0) {
      return null;
    }
    String toMatch = segments[index];

    if (index == segments.length - 1) {
      toMatch += ".class";

      for (File file : children) {
        if (file.getName().equals(toMatch)) {
          return file;
        }
      }
      return null;
    }

    String innerClassName = null;

    for (File file : children) {
      if (file.isDirectory()) {
        if (toMatch.equals(file.getName())) {
          return findClassFile(file, segments, index + 1);
        }
      }
      else if (file.getName().startsWith(toMatch)) {
        if (innerClassName == null) {
          final StringBuilder builder = new StringBuilder(segments[index]);
          for (int i = index + 1; i < segments.length; i++) {
            builder.append('$');
            builder.append(segments[i]);
          }
          builder.append(".class");

          innerClassName = builder.toString();
        }

        if (file.getName().equals(innerClassName)) {
          return file;
        }
      }
    }
    return null;
  }

  @Nullable
  private Class<?> loadClassFromJar(String name) {
    if (mJarClassLoader == null) {
      final URL[] externalJars = getExternalJars();
      mJarClassLoader = new URLClassLoader(externalJars, this);
    }

    try {
      mInsideJarClassLoader = true;
      return mJarClassLoader.loadClass(name);
    }
    catch (ClassNotFoundException e) {
      LOG.debug(e);
      return null;
    }
    finally {
      mInsideJarClassLoader = false;
    }
  }

  private URL[] getExternalJars() {
    final List<URL> result = new ArrayList<URL>();

    for (VirtualFile libFile : AndroidRootUtil.getExternalLibraries(myModule)) {
      if ("jar".equals(libFile.getExtension())) {
        final File file = new File(libFile.getPath());
        if (file.exists()) {
          try {
            result.add(file.toURI().toURL());
          }
          catch (MalformedURLException e) {
            LOG.error(e);
          }
        }
      }
    }
    return result.toArray(new URL[result.size()]);
  }
}