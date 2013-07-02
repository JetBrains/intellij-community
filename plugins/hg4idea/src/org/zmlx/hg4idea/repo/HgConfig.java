package org.zmlx.hg4idea.repo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgShowConfigCommand;

import java.util.Collections;
import java.util.Map;

/**
 * @author Nadya Zabrodina
 */
public class HgConfig implements HgUpdater {

  @NotNull private VirtualFile myRepo;
  @NotNull private Project myProject;
  @NotNull private Map<String, String> myConfigMap = Collections.emptyMap();


  public HgConfig(@NotNull Project project, @NotNull VirtualFile repo) {
    myProject = project;
    myRepo = repo;
    update(myProject, myRepo);
    myProject.getMessageBus().connect().subscribe(HgVcs.UPDATE_CONFIG_TOPIC, this);
  }


  @Override
  public void update(@NotNull Project project, @Nullable VirtualFile root) {
    // todo: may be should change showconfigCommand to parse hgrc file
    // but default values for extension and repository root are not included in hgrc, so perform showconfig is better
    // in windows configuration Mercurial.ini file may be used instead of hgrc
    myConfigMap = new HgShowConfigCommand(myProject).execute(myRepo);
  }

  @Nullable
  public String getDefaultPath() {
    return myConfigMap.get("paths.default");
  }

  @Nullable
  public String getDefaultPushPath() {
    String path = myConfigMap.get("paths.default-push");
    if (path == null) {
      path = myConfigMap.get("paths.default");
    }
    return path;
  }

  @Nullable
  public String getNamedConfig(@Nullable String configName) {
    if (StringUtil.isEmptyOrSpaces(configName)) {
      return null;
    }
    return myConfigMap.get(configName);
  }
}
