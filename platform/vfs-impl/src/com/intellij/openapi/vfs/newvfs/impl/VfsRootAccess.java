// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public final class VfsRootAccess {
  private static final boolean SHOULD_PERFORM_ACCESS_CHECK =
    System.getenv("NO_FS_ROOTS_ACCESS_CHECK") == null && System.getProperty("NO_FS_ROOTS_ACCESS_CHECK") == null;

  // we don't want test subclasses to accidentally remove allowed files added by base classes
  private static final Set<String> ourAdditionalRoots = CollectionFactory.createFilePathSet(); // guarded by `ourAdditionalRoots`
  private static boolean insideGettingRoots;

  @TestOnly
  static void assertAccessInTests(@NotNull VirtualFile child, @NotNull NewVirtualFileSystem delegate) {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    if (SHOULD_PERFORM_ACCESS_CHECK &&
        app.isUnitTestMode() &&
        app.isComponentCreated() &&
        !ApplicationManagerEx.isInStressTest()) {
      if (delegate != LocalFileSystem.getInstance() && delegate != JarFileSystem.getInstance()) {
        return;
      }

      // root' children are loaded always
      if (child.getParent() == null || child.getParent().getParent() == null) {
        return;
      }

      Set<String> allowed = allowedRoots();
      boolean isUnder = allowed == null || allowed.isEmpty();

      if (!isUnder) {
        VirtualFile local = child;
        if (delegate == JarFileSystem.getInstance()) {
          local = JarFileSystem.getInstance().getVirtualFileForJar(child);
          assert local != null : child;
        }
        for (String root : allowed) {
          if (VfsUtilCore.isAncestorOrSelf(root, local)) {
            isUnder = true;
            break;
          }
          if (root.startsWith(JarFileSystem.PROTOCOL_PREFIX)) {
            String rootLocalPath = FileUtil.toSystemIndependentName(PathUtil.toPresentableUrl(root));
            isUnder = VfsUtilCore.isAncestorOrSelf(rootLocalPath, local);
            if (isUnder) break;
          }
        }
      }

      assert isUnder : "File accessed outside allowed roots: " + child + ";\nAllowed roots: " + new ArrayList<>(allowed);
    }
  }

  // null means we were unable to get roots, so do not check access
  private static @Nullable Set<String> allowedRoots() {
    if (insideGettingRoots) return null;

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 0) return null;

    Set<String> allowed = CollectionFactory.createFilePathSet();
    allowed.add(FileUtil.toSystemIndependentName(PathManager.getHomePath()));

    // In plugin development environment PathManager.getHomePath() returns path like "~/.IntelliJIdea/system/plugins-sandbox/test" when running tests
    // The following is to avoid errors in tests like "File accessed outside allowed roots: file://C:/Program Files/idea/lib/idea.jar"
    String homePath2 = PathManager.getHomePathFor(Application.class);
    if (homePath2 != null) {
      allowed.add(FileUtil.toSystemIndependentName(homePath2));
    }

    try {
      URL outUrl = Application.class.getResource("/");
      if (outUrl != null) {
        String output = new File(outUrl.toURI()).getParentFile().getParentFile().getPath();
        allowed.add(FileUtil.toSystemIndependentName(output));
      }
    }
    catch (URISyntaxException | IllegalArgumentException ignored) { }

    try {
      allowed.add(FileUtil.toSystemIndependentName(getJavaHome()));
      allowed.add(FileUtil.toSystemIndependentName(new File(FileUtil.getTempDirectory()).getParent()));
      allowed.add(FileUtil.toSystemIndependentName(System.getProperty("java.io.tmpdir")));

      String userHome = FileUtil.toSystemIndependentName(SystemProperties.getUserHome());
      allowed.add(userHome);

      String mavenHome = resolvedPath(userHome + "/.m2");
      if (!mavenHome.startsWith(userHome + '/')) {
        allowed.add(mavenHome);
      }
      mavenHome = resolvedPath(userHome + "/.m2/repository");
      if (!mavenHome.startsWith(userHome + '/')) {
        allowed.add(mavenHome);
      }

      String gradleHome = resolvedPath(userHome + "/.gradle");
      if (gradleHome.startsWith(userHome + '/')) {
        allowed.add(gradleHome);
      }
      gradleHome = System.getenv("GRADLE_USER_HOME");
      if (gradleHome != null) {
        allowed.add(FileUtil.toSystemIndependentName(gradleHome));
      }

      if (SystemInfo.isWindows) {
        String wslName = System.getProperty("wsl.distribution.name");
        if (wslName != null) {
          allowed.add(FileUtil.toSystemIndependentName("\\\\wsl$\\" + wslName));
        }
      }
      else {
        // see IDEA-167037 (The assertion "File accessed outside allowed root" is triggered by files symlinked from a JDK directory)
        allowed.add("/etc");
        allowed.add("/private/etc");
      }

      for (final Project project : openProjects) {
        if (!project.isInitialized()) {
          return null; // all is allowed
        }
        ReadAction.run(() -> {
          for (String url : ProjectRootManager.getInstance(project).getContentRootUrls()) {
            allowed.add(VfsUtilCore.urlToPath(url));
          }
          for (Module module : ModuleManager.getInstance(project).getModules()) {
            Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
            if (moduleSdk != null) {
              String homePath = moduleSdk.getHomePath();
              if (homePath != null) {
                allowed.add(homePath);
              }
            }
          }
          for (String url : getAllRootUrls(project)) {
            allowed.add(StringUtil.trimEnd(VfsUtilCore.urlToPath(url), JarFileSystem.JAR_SEPARATOR));
          }
          String location = project.getBasePath();
          assert location != null : project;
          allowed.add(FileUtil.toSystemIndependentName(location));
        });
      }
    }
    catch (Error ignored) {
      // sometimes `library.getRoots()` may crash if called during library modification
    }

    synchronized (ourAdditionalRoots) {
      allowed.addAll(ourAdditionalRoots);
    }

    return allowed;
  }

  private static String getJavaHome() {
    String javaHome = SystemProperties.getJavaHome();
    if (JdkUtil.checkForJre(javaHome) && !JdkUtil.checkForJdk(javaHome)) {
      String javaHomeParent = PathUtil.getParentPath(javaHome);
      if (JdkUtil.checkForJre(javaHomeParent) && JdkUtil.checkForJdk(javaHomeParent)) {
        javaHome = javaHomeParent;
      }
    }
    return javaHome;
  }

  private static String resolvedPath(String path) {
    try {
      return FileUtil.toSystemIndependentName(Path.of(path).toRealPath().toString());
    }
    catch (IOException e) {
      return path;
    }
  }

  private static Collection<String> getAllRootUrls(Project project) {
    insideGettingRoots = true;
    try {
      Set<String> roots = CollectionFactory.createSmallMemoryFootprintSet();
      OrderEnumerator enumerator = ProjectRootManager.getInstance(project).orderEntries().using(new DefaultModulesProvider(project));
      ContainerUtil.addAll(roots, enumerator.classes().getUrls());
      ContainerUtil.addAll(roots, enumerator.sources().getUrls());
      return roots;
    }
    finally {
      insideGettingRoots = false;
    }
  }

  @TestOnly
  public static void allowRootAccess(@NotNull Disposable disposable, @NotNull String @NotNull ... roots) {
    if (roots.length == 0) return;
    doAllow(roots);
    Disposer.register(disposable, () -> disallowRootAccess(roots));
  }

  private static void doAllow(String... roots) {
    synchronized (ourAdditionalRoots) {
      for (String root : roots) {
        String path = StringUtil.trimEnd(FileUtil.toSystemIndependentName(root), '/');
        if (path.isEmpty()) {
          throw new IllegalArgumentException("Must not pass empty pat but got: '" + Arrays.toString(roots) + "'");
        }
        ourAdditionalRoots.add(path);
      }
    }
  }

  private static void disallowRootAccess(String... roots) {
    synchronized (ourAdditionalRoots) {
      for (String root : roots) {
        ourAdditionalRoots.remove(StringUtil.trimEnd(FileUtil.toSystemIndependentName(root), '/'));
      }
    }
  }
}
