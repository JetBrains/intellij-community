package com.intellij.jps.cache;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class JpsCacheUtils {
  public static Map<Module, List<VirtualFile>> createModuleToSourceRootsMap(Project project) {
    ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);
    VirtualFile[] sourcesRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
    return Arrays.stream(sourcesRoots).collect(Collectors.groupingBy(sourceRoot -> projectFileIndex.getModuleForFile(sourceRoot)));
  }

  public static Map<String, Module> createModuleNameToModuleMap(Project project) {
    return Arrays.stream(ModuleManager.getInstance(project).getModules()).collect(Collectors.toMap(module -> module.getName(),
                                                                                                   Function.identity()));
  }

  public static String getPluginStorageDir(Project project) throws NoSuchAlgorithmException {
    File pluginsDir = new File(PathManager.getPluginsPath());
    String projectPathHash = "";
    MessageDigest messageDigest = MessageDigest.getInstance("MD5");
    messageDigest.update(project.getBasePath().getBytes());
    projectPathHash = new String(messageDigest.digest());
    return FileUtil.join(pluginsDir.getPath(), JpsCachePluginComponent.PLUGIN_NAME, project.getName() + "_" + projectPathHash);
  }
}
