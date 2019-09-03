package com.intellij.jps.cache.hashing;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JpsCacheUtils {
  private static final String PLUGIN_NAME = "jpsCachePlugin";

  private JpsCacheUtils() {}

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
}
