package org.zmlx.hg4idea.repo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgShowConfigCommand;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Nadya Zabrodina
 */
public class HgConfig {

  @NotNull private final Map<String, Map<String, String>> myConfigMap;

  public static HgConfig getInstance(Project project, VirtualFile root) {
    return new HgConfig(project, root);
  }

  private HgConfig(@NotNull Project project, @NotNull VirtualFile repo) {
    // todo: may be should change showconfigCommand to parse hgrc file
    // but default values for extension and repository root are not included in hgrc, so perform showconfig is better
    // in windows configuration Mercurial.ini file may be used instead of hgrc
    myConfigMap = new HgShowConfigCommand(project).execute(repo);
  }

  @Nullable
  public String getDefaultPath() {
    return getNamedConfig("paths", "default");
  }

  @Nullable
  public String getDefaultPushPath() {
    String path = getNamedConfig("paths", "default-push");
    return path != null ? path : getNamedConfig("paths", "default");
  }

  @Nullable
  public String getNamedConfig(@NotNull String sectionName, @Nullable String configName) {
    if (StringUtil.isEmptyOrSpaces(sectionName) || StringUtil.isEmptyOrSpaces(configName)) {
      return null;
    }
    Map<String, String> sectionValues = myConfigMap.get(sectionName);
    return sectionValues != null ? sectionValues.get(configName) : null;
  }

  @NotNull
  public Collection<String> getPaths() {
    Map<String, String> pathOptions = myConfigMap.get("paths");
    return pathOptions != null ? pathOptions.values() : Collections.<String>emptyList();
  }
}
