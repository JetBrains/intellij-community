package com.intellij.jps.cache;

import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE;
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;

public class JpsCachesUtils {
  private static final List<JpsModuleSourceRootType<?>> PRODUCTION_SOURCE_ROOTS = Arrays.asList(SOURCE, RESOURCE);
  private static final List<JpsModuleSourceRootType<?>> TEST_SOURCE_ROOTS = Arrays.asList(TEST_SOURCE, TEST_RESOURCE);
  public static final String PLUGIN_NAME = "jps-cache-loader";

  private JpsCachesUtils() {}

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
