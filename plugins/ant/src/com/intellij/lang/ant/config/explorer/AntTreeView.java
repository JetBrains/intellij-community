/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.config.explorer;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;

import java.util.*;

/**
 * @author VISTALL
 * @since 14:20/08.03.13
 */
public enum AntTreeView {
  NO_GROUPING {
    @Override
    public Object[] getRootChildren(Project project) {
      final AntConfiguration configuration = AntConfiguration.getInstance(project);

      final AntBuildFile[] buildFiles = configuration.getBuildFiles();
      if(buildFiles.length == 0) {
        return new Object[]{AntBundle.message("ant.tree.structure.no.build.files.message")};
      }
      AntBuildFileGroupManager groupManager = AntBuildFileGroupManager.getInstance(project);
      final AntBuildFileGroup[] firstLevelGroups = groupManager.getFirstLevelGroups();
      if(firstLevelGroups.length == 0) {
        return buildFiles;
      }

      List<Object> objects = new ArrayList<Object>();
      Collections.addAll(objects, firstLevelGroups);
      for (AntBuildFile buildFile : buildFiles) {
        if(groupManager.findGroup(buildFile) == null) {
          objects.add(buildFile);
        }
      }

      return ArrayUtil.toObjectArray(objects);
    }

    @Override
    public Object[] getChildren(Project project, Object element, boolean isFilterTargets) {
      if(element instanceof AntBuildFileGroup) {
        return ArrayUtil.mergeArrays(AntBuildFileGroupManager.getInstance(project).getFilesForGroup((AntBuildFileGroup) element), ((AntBuildFileGroup)element).getChildren(), ArrayUtil.OBJECT_ARRAY_FACTORY);
      }
      return super.getChildren(project, element, isFilterTargets);
    }
  },

  MODULE_GROUPING {
    private static final String ourNoModuleObject = "<no module>";

    @Override
    public Object[] getRootChildren(Project project) {
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      Object[] objects = new Object[modules.length + 1];
      objects[0] = ourNoModuleObject;
      System.arraycopy(modules, 0, objects, 1, modules.length);
      return objects;
    }

    @Override
    public Object[] getChildren(Project project, Object element, boolean isFilterTargets) {
      if(element == ourNoModuleObject || element instanceof Module)
      {
        List<Object> objects = new ArrayList<Object>();

        AntConfiguration antConfiguration = AntConfiguration.getInstance(project);

        for (AntBuildFile buildFile : antConfiguration.getBuildFiles()) {
          final VirtualFile virtualFile = buildFile.getVirtualFile();
          if(virtualFile == null) {
            continue;
          }

          final Module moduleForFile = ModuleUtilCore.findModuleForFile(virtualFile, project);

          Object keyMap = moduleForFile == null ? ourNoModuleObject : moduleForFile;
          if(keyMap == element) {
            objects.add(buildFile);
          }
        }
        return objects.toArray();
      }
      return super.getChildren(project, element, isFilterTargets);
    }
  };

  private static final Comparator<AntBuildTarget> ourTargetComparator = new Comparator<AntBuildTarget>() {
    @Override
    public int compare(final AntBuildTarget target1, final AntBuildTarget target2) {
      final String name1 = target1.getDisplayName();
      if (name1 == null) return Integer.MIN_VALUE;
      final String name2 = target2.getDisplayName();
      if (name2 == null) return Integer.MAX_VALUE;
      return name1.compareToIgnoreCase(name2);
    }
  };

  public abstract Object[] getRootChildren(Project project);

  public Object[] getChildren(Project project, Object element, boolean isFilterTargets)
  {
    if (element instanceof AntBuildFile) {

      final AntConfiguration configuration = AntConfiguration.getInstance(project) ;
      final AntBuildFile buildFile = (AntBuildFile)element;
      final AntBuildModel model = buildFile.getModel();

      final List<AntBuildTarget> targets =
        new ArrayList<AntBuildTarget>(Arrays.asList(isFilterTargets ? model.getFilteredTargets() : model.getTargets()));
      Collections.sort(targets, ourTargetComparator);

      final List<AntBuildTarget> metaTargets = Arrays.asList(configuration.getMetaTargets(buildFile));
      Collections.sort(metaTargets, ourTargetComparator);
      targets.addAll(metaTargets);

      return targets.toArray(new AntBuildTarget[targets.size()]);
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
