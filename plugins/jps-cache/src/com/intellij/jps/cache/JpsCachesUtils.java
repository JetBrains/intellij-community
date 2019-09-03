package com.intellij.jps.cache;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE;
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;

public class JpsCachesUtils {
  private static final List<JpsModuleSourceRootType<?>> PRODUCTION_SOURCE_ROOTS = Arrays.asList(SOURCE, RESOURCE);
  private static final List<JpsModuleSourceRootType<?>> TEST_SOURCE_ROOTS = Arrays.asList(TEST_SOURCE, TEST_RESOURCE);
  private static final String PLUGIN_NAME = "jpsCachePlugin";

  private JpsCachesUtils() {}

  public static Map<String, Module> createModuleNameToModuleMap(Project project) {
    return Arrays.stream(ModuleManager.getInstance(project).getModules()).collect(Collectors.toMap(module -> module.getName(),
                                                                                                   Function.identity()));
  }

  public static String getPluginStorageDir(Project project) throws NoSuchAlgorithmException {
    File pluginsDir = new File(PathManager.getPluginsPath());
    MessageDigest messageDigest = MessageDigest.getInstance("MD5");
    messageDigest.update(project.getBasePath().getBytes());
    String projectPathHash = DatatypeConverter.printHexBinary(messageDigest.digest());
    return FileUtil.join(pluginsDir.getPath(), PLUGIN_NAME, project.getName() + "_" + projectPathHash);
  }


  public static File[] getProductionSourceRootFiles(ModuleRootManager moduleRootManager) {
    return getSourceRootFiles(moduleRootManager, PRODUCTION_SOURCE_ROOTS);
  }
  public static File[] getTestSourceRootFiles(ModuleRootManager moduleRootManager) {
    return getSourceRootFiles(moduleRootManager, TEST_SOURCE_ROOTS);
  }

  private static File[] getSourceRootFiles(ModuleRootManager moduleRootManager, @NotNull List<JpsModuleSourceRootType<?>> sourceRootTypes) {
    return sourceRootTypes.stream().map(moduleRootManager::getSourceRoots).flatMap(List::stream)
                                                                                .map(vf -> new File(vf.getPath()))
                                                                                .toArray(File[]::new);
  }
}
