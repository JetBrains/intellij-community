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
package com.intellij.lang.ant.config.impl.group;

import com.intellij.lang.ant.config.*;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 12:27/09.03.13
 */
@State(
  name = "AntBuildFileGroupManager",
  storages = {@Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/ant.xml", scheme = StorageScheme.DIRECTORY_BASED)})
public class AntBuildFileGroupManagerImpl extends AntBuildFileGroupManager implements PersistentStateComponent<Element> {
  private final Map<AntBuildFileGroup, List<VirtualFile>> myFileGroupList = new HashMap<AntBuildFileGroup, List<VirtualFile>>();
  private final List<AntBuildFileGroup> myGroups = new ArrayList<AntBuildFileGroup>();

  private final Project myProject;

  public AntBuildFileGroupManagerImpl(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public AntBuildFileGroup createGroup(@Nullable AntBuildFileGroup parent, @NotNull String name) {
    AntBuildFileGroupImpl group = new AntBuildFileGroupImpl(name, parent);

    if (parent != null) {
      ((AntBuildFileGroupImpl)parent).getChildrenAsList().add(group);
    }
    else {
      myGroups.add(group);
    }
    return group;
  }

  @Override
  public void moveToGroup(@NotNull AntBuildFile file, @Nullable AntBuildFileGroup group) {
    for (Map.Entry<AntBuildFileGroup, List<VirtualFile>> entry : myFileGroupList.entrySet()) {
      if (entry.getValue().contains(file.getVirtualFile())) {
        entry.getValue().remove(file.getVirtualFile());
        break;
      }
    }

    if (group == null) {
      return;
    }
    List<VirtualFile> virtualFiles = myFileGroupList.get(group);
    if (virtualFiles == null) {
      myFileGroupList.put(group, virtualFiles = new ArrayList<VirtualFile>());
    }
    virtualFiles.add(file.getVirtualFile());
  }

  @Override
  public AntBuildFile[] getFilesForGroup(@NotNull AntBuildFileGroup group) {
    final List<VirtualFile> virtualFiles = myFileGroupList.get(group);
    if (virtualFiles == null || virtualFiles.isEmpty()) {
      return AntBuildFile.EMPTY_ARRAY;
    }
    List<AntBuildFile> files = new ArrayList<AntBuildFile>();
    final AntConfigurationBase antConfiguration = AntConfigurationBase.getInstance(myProject);
    final PsiManager manager = PsiManager.getInstance(myProject);
    for (VirtualFile virtualFile : virtualFiles) {
      final PsiFile file = manager.findFile(virtualFile);
      if (!(file instanceof XmlFile)) {
        continue;
      }
      final AntBuildFileBase antBuildFile = antConfiguration.getAntBuildFile(file);
      if (antBuildFile == null) {
        continue;
      }
      files.add(antBuildFile);
    }

    return files.toArray(new AntBuildFile[files.size()]);
  }

  @NotNull
  @Override
  public AntBuildFileGroup[] getFirstLevelGroups() {
    return myGroups.isEmpty() ? AntBuildFileGroup.EMPTY_ARRAY : myGroups.toArray(new AntBuildFileGroup[myGroups.size()]);
  }

  @Override
  public AntBuildFileGroup findGroup(@NotNull AntBuildFile buildFile) {
    for (Map.Entry<AntBuildFileGroup, List<VirtualFile>> entry : myFileGroupList.entrySet()) {
      if (entry.getValue().contains(buildFile.getVirtualFile())) {
        return entry.getKey();
      }
    }
    return null;
  }

  @Override
  public void removeGroup(@NotNull AntBuildFileGroup buildGroup) {
    myFileGroupList.remove(buildGroup);
    myGroups.remove(buildGroup);

    final AntBuildFileGroup parent = buildGroup.getParent();
    if(parent != null) {
      ((AntBuildFileGroupImpl)parent).getChildrenAsList().remove(buildGroup);
    }

    for (AntBuildFileGroup child : buildGroup.getChildren()) {
      removeGroup(child);
    }
  }

  @Nullable
  @Override
  public Element getState() {
    Element groupsElement = new Element("ant-groups");
    for (AntBuildFileGroup group : myGroups) {
      writeToElement(group, groupsElement);
    }
    return groupsElement;
  }

  private void writeToElement(AntBuildFileGroup group, Element parent) {
    final Element element = new Element("ant-group");
    element.setAttribute("name", group.getName());

    for (AntBuildFileGroup childGroup : group.getChildren()) {
      writeToElement(childGroup, element);
    }

    for (AntBuildFile file : getFilesForGroup(group)) {
      final Element fileElement = new Element("file");
      fileElement.setText(file.getVirtualFile().getUrl());
      element.addContent(fileElement);
    }

    parent.addContent(element);
  }

  @Override
  public void loadState(Element state) {
    final List<Element> children = state.getChildren("ant-group");
    for (Element child : children) {
      readElement(null, child);
    }
  }

  private void readElement(AntBuildFileGroup parentGroup, Element child) {
    String name = child.getAttributeValue("name");

    final AntBuildFileGroup newChildGroup = createGroup(parentGroup, name);
    final VirtualFileManager vfManager = VirtualFileManager.getInstance();

    final List<Element> children = child.getChildren();
    for (Element element : children) {
      String elementName = element.getName();
      if (elementName.equals("ant-group")) {
        readElement(newChildGroup, element);
      }
      else if (elementName.equals("file")) {
        VirtualFile virtualFile = vfManager.findFileByUrl(element.getText());
        if (virtualFile != null) {
          List<VirtualFile> virtualFiles = myFileGroupList.get(newChildGroup);
          if (virtualFiles == null) {
            myFileGroupList.put(newChildGroup, virtualFiles = new ArrayList<VirtualFile>());
          }
          virtualFiles.add(virtualFile);
        }
      }
    }
  }
}
