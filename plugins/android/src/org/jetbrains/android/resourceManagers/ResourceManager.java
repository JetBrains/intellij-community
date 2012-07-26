/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.resourceManagers;

import com.android.resources.ResourceType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.DomElement;
import org.jetbrains.android.AndroidIdIndex;
import org.jetbrains.android.AndroidValueResourcesIndex;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Collections.addAll;

/**
 * @author coyote
 */
public abstract class ResourceManager {
  protected final Module myModule;
  protected final AndroidFacet myFacet;

  protected ResourceManager(@NotNull AndroidFacet facet) {
    myFacet = facet;
    myModule = facet.getModule();
  }

  public Module getModule() {
    return myModule;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  @NotNull
  public abstract VirtualFile[] getAllResourceDirs();

  @Nullable
  public abstract VirtualFile getResourceDir();

  public boolean processFileResources(@Nullable String resourceType, @NotNull FileResourceProcessor processor) {
    return processFileResources(resourceType, processor, true);
  }

  public boolean processFileResources(@Nullable String resourceType, @NotNull FileResourceProcessor processor, boolean publicOnly) {
    for (VirtualFile resSubdir : getResourceSubdirs(resourceType)) {
      final String resType = AndroidCommonUtils.getResourceTypeByDirName(resSubdir.getName());

      if (resType != null) {
        assert resourceType == null || resourceType.equals(resType);
        for (VirtualFile resFile : resSubdir.getChildren()) {
          final String resName = AndroidCommonUtils.getResourceName(resType, resFile.getName());

          if (!resFile.isDirectory() && (!publicOnly || isResourcePublic(resType, resName))) {
            if (!processor.process(resFile, resName, resType)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  public boolean isResourceDir(@NotNull VirtualFile dir) {
    return dir.equals(getResourceDir());
  }

  @NotNull
  public VirtualFile[] getResourceOverlayDirs() {
    return VirtualFile.EMPTY_ARRAY;
  }

  protected boolean isResourcePublic(@NotNull String type, @NotNull String name) {
    return true;
  }

  @NotNull
  public List<VirtualFile> getResourceSubdirs(@Nullable String resourceType) {
    return AndroidResourceUtil.getResourceSubdirs(resourceType, getAllResourceDirs());
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull final String resType1,
                                         @Nullable final String resName1,
                                         final boolean distinguishDelimetersInName,
                                         @NotNull final String... extensions) {
    final List<PsiFile> result = new ArrayList<PsiFile>();
    final Set<String> extensionSet = new HashSet<String>();
    addAll(extensionSet, extensions);

    processFileResources(resType1, new FileResourceProcessor() {
      @Override
      public boolean process(@NotNull final VirtualFile resFile, @NotNull String resName, @NotNull String resFolderType) {
        final String extension = resFile.getExtension();

        if ((extensions.length == 0 || extensionSet.contains(extension)) &&
            (resName1 == null || AndroidUtils.equal(resName1, resName, distinguishDelimetersInName))) {
          final PsiFile file = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
            @Nullable
            public PsiFile compute() {
              return PsiManager.getInstance(myModule.getProject()).findFile(resFile);
            }
          });
          if (file != null) {
            result.add(file);
          }
        }
        return true;
      }
    });
    return result;
  }

  public List<PsiFile> findResourceFiles(@NotNull String resType, @NotNull String resName, @NotNull String... extensions) {
    return findResourceFiles(resType, resName, true, extensions);
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull String resType) {
    return findResourceFiles(resType, null, true);
  }

  protected List<Pair<Resources, VirtualFile>> getResourceElements(@Nullable Set<VirtualFile> files) {
    return getRootDomElements(Resources.class, files);
  }

  private <T extends DomElement> List<Pair<T, VirtualFile>> getRootDomElements(@NotNull Class<T> elementType,
                                                                               @Nullable Set<VirtualFile> files) {
    final List<Pair<T, VirtualFile>> result = new ArrayList<Pair<T, VirtualFile>>();
    for (VirtualFile file : getAllValueResourceFiles()) {
      if ((files == null || files.contains(file)) && file.isValid()) {
        final T element = AndroidUtils.loadDomElement(myModule, file, elementType);
        if (element != null) {
          result.add(new Pair<T, VirtualFile>(element, file));
        }
      }
    }
    return result;
  }

  @NotNull
  protected Set<VirtualFile> getAllValueResourceFiles() {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();

    for (VirtualFile valueResourceDir : getResourceSubdirs("values")) {
      for (VirtualFile valueResourceFile : valueResourceDir.getChildren()) {
        if (!valueResourceFile.isDirectory() && valueResourceFile.getFileType().equals(StdFileTypes.XML)) {
          files.add(valueResourceFile);
        }
      }
    }
    return files;
  }

  protected List<ResourceElement> getValueResources(@NotNull final String resourceType, @Nullable Set<VirtualFile> files) {
    final List<ResourceElement> result = new ArrayList<ResourceElement>();
    List<Pair<Resources, VirtualFile>> resourceFiles = getResourceElements(files);
    for (final Pair<Resources, VirtualFile> pair : resourceFiles) {
      final Resources resources = pair.getFirst();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          if (!resources.isValid() || myModule.isDisposed() || myModule.getProject().isDisposed()) {
            return;
          }
          final List<ResourceElement> valueResources = AndroidResourceUtil.getValueResourcesFromElement(resourceType, resources);
          for (ResourceElement valueResource : valueResources) {
            final String resName = valueResource.getName().getValue();

            if (resName != null && isResourcePublic(resourceType, resName)) {
              result.add(valueResource);
            }
          }
        }
      });
    }
    return result;
  }

  @Nullable
  public String getValueResourceType(@NotNull XmlTag tag) {
    String fileResType = getFileResourceType(tag.getContainingFile());
    if ("values".equals(fileResType)) {
      return tag.getName();
    }
    return null;
  }

  @Nullable
  public String getFileResourceType(@NotNull final PsiFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Nullable
      @Override
      public String compute() {
        PsiDirectory dir = file.getContainingDirectory();
        if (dir == null) return null;
        PsiDirectory possibleResDir = dir.getParentDirectory();
        if (possibleResDir == null || !isResourceDir(possibleResDir.getVirtualFile())) {
          return null;
        }
        String type = AndroidCommonUtils.getResourceTypeByDirName(dir.getName());
        if (type == null) return null;
        return type;
      }
    });
  }

  @NotNull
  public Set<String> getFileResourcesNames(@NotNull final String resourceType) {
    final Set<String> result = new HashSet<String>();

    processFileResources(resourceType, new FileResourceProcessor() {
      @Override
      public boolean process(@NotNull VirtualFile resFile, @NotNull String resName, @NotNull String resFolderType) {
        result.add(resName);
        return true;
      }
    });
    return result;
  }

  @NotNull
  public Collection<String> getValueResourceNames(@NotNull final String resourceType) {
    final Set<String> result = new HashSet<String>();
    final boolean attr = ResourceType.ATTR.getName().equals(resourceType);

    for (ResourceEntry entry : getValueResourceEntries(resourceType)) {
      final String name = entry.getName();

      if (!attr || !name.startsWith("android:")) {
        result.add(name);
      }
    }
    return result;
  }

  @NotNull
  public Collection<ResourceEntry> getValueResourceEntries(@NotNull final String resourceType) {
    final ResourceType type = ResourceType.getEnum(resourceType);

    if (type == null) {
      return Collections.emptyList();
    }

    final FileBasedIndex index = FileBasedIndex.getInstance();
    final ResourceEntry typeMarkerEntry = AndroidValueResourcesIndex.createTypeMarkerKey(resourceType);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myModule.getProject());

    final Map<VirtualFile, Set<ResourceEntry>> file2resourceSet = new HashMap<VirtualFile, Set<ResourceEntry>>();
    for (Set<ResourceEntry> entrySet : index.getValues(AndroidValueResourcesIndex.INDEX_ID, typeMarkerEntry, scope)) {
      for (ResourceEntry entry : entrySet) {
        final Collection<VirtualFile> files = index.getContainingFiles(AndroidValueResourcesIndex.INDEX_ID, entry, scope);

        for (VirtualFile file : files) {
          Set<ResourceEntry> resourcesInFile = file2resourceSet.get(file);

          if (resourcesInFile == null) {
            resourcesInFile = new HashSet<ResourceEntry>();
            file2resourceSet.put(file, resourcesInFile);
          }
          resourcesInFile.add(entry);
        }
      }
    }
    final List<ResourceEntry> result = new ArrayList<ResourceEntry>();

    for (VirtualFile file : getAllValueResourceFiles()) {
      final Set<ResourceEntry> entries = file2resourceSet.get(file);

      if (entries != null) {
        for (ResourceEntry entry : entries) {
          if (isResourcePublic(entry.getType(), entry.getName())) {
            result.add(entry);
          }
        }
      }
    }
    return result;
  }

  @NotNull
  public Collection<String> getResourceNames(@NotNull String type) {
    final Set<String> result = new HashSet<String>();
    result.addAll(getValueResourceNames(type));
    result.addAll(getFileResourcesNames(type));
    if (type.equals(ResourceType.ID.getName())) {
      result.addAll(getIds());
    }
    return result;
  }

  @Nullable
  public abstract AttributeDefinitions getAttributeDefinitions();

  // searches only declarations such as "@+id/..."
  @NotNull
  public List<IdResourceInfo> findIdDeclarationInfos(@NotNull final String id) {
    if (!isResourcePublic(ResourceType.ID.getName(), id)) {
      return Collections.emptyList();
    }

    final List<IdResourceInfo> result = new ArrayList<IdResourceInfo>();
    final Collection<VirtualFile> files =
      FileBasedIndex.getInstance().getContainingFiles(AndroidIdIndex.INDEX_ID, id, GlobalSearchScope.allScope(myModule.getProject()));
    final Set<VirtualFile> fileSet = new HashSet<VirtualFile>(files);

    for (VirtualFile subdir : getResourceSubdirsToSearchIds()) {
      for (VirtualFile file : subdir.getChildren()) {
        if (fileSet.contains(file)) {
          result.add(new IdResourceInfo(id, file, myModule.getProject()));
        }
      }
    }
    return result;
  }

  @NotNull
  public List<PsiElement> findIdDeclarations(@NotNull String id) {
    final List<IdResourceInfo> infos = findIdDeclarationInfos(id);

    if (infos.size() == 0) {
      return Collections.emptyList();
    }
    final List<PsiElement> result = new ArrayList<PsiElement>();

    for (IdResourceInfo info : infos) {
      final PsiElement element = info.computeXmlElement();

      if (element != null) {
        result.add(element);
      }
    }
    return result;
  }

  @NotNull
  public Collection<String> getIds() {
    final Project project = myModule.getProject();

    if (myModule.isDisposed() || project.isDisposed()) {
      return Collections.emptyList();
    }
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myModule.getProject());

    final FileBasedIndex index = FileBasedIndex.getInstance();
    final Map<VirtualFile, Set<String>> file2ids = new HashMap<VirtualFile, Set<String>>();

    for (String key : index.getAllKeys(AndroidIdIndex.INDEX_ID, project)) {
      if (!AndroidIdIndex.MARKER.equals(key)) {
        if (index.getValues(AndroidIdIndex.INDEX_ID, key, scope).size() > 0) {

          for (VirtualFile file : index.getContainingFiles(AndroidIdIndex.INDEX_ID, key, scope)) {
            Set<String> ids = file2ids.get(file);

            if (ids == null) {
              ids = new HashSet<String>();
              file2ids.put(file, ids);
            }
            ids.add(key);
          }
        }
      }
    }
    final Set<String> result = new HashSet<String>();

    for (VirtualFile resSubdir : getResourceSubdirsToSearchIds()) {
      for (VirtualFile resFile : resSubdir.getChildren()) {
        final Set<String> ids = file2ids.get(resFile);

        if (ids != null) {
          for (String id : ids) {
            if (isResourcePublic(ResourceType.ID.getName(), id)) {
              result.add(id);
            }
          }
        }
      }
    }
    return result;
  }

  @NotNull
  public List<VirtualFile> getResourceSubdirsToSearchIds() {
    final List<VirtualFile> resSubdirs = new ArrayList<VirtualFile>();
    for (ResourceType type : AndroidCommonUtils.ID_PROVIDING_RESOURCE_TYPES) {
      resSubdirs.addAll(getResourceSubdirs(type.getName()));
    }
    return resSubdirs;
  }

  public List<ResourceElement> findValueResources(@NotNull String resType, @NotNull String resName) {
    return findValueResources(resType, resName, true);
  }

  // not recommended to use, because it is too slow
  @NotNull
  public List<ResourceElement> findValueResources(@NotNull String resourceType,
                                                  @NotNull String resourceName,
                                                  boolean distinguishDelimitersInName) {
    final List<ValueResourceInfoImpl> resources = findValueResourceInfos(resourceType, resourceName, distinguishDelimitersInName);
    final List<ResourceElement> result = new ArrayList<ResourceElement>();

    for (ValueResourceInfoImpl resource : resources) {
      final ResourceElement domElement = resource.computeDomElement();

      if (domElement != null) {
        result.add(domElement);
      }
    }
    return result;
  }

  @NotNull
  public List<ValueResourceInfoImpl> findValueResourceInfos(@NotNull String resourceType,
                                                            @NotNull String resourceName,
                                                            boolean distinguishDelimetersInName) {
    final ResourceType type = ResourceType.getEnum(resourceType);
    if (type == null) {
      return Collections.emptyList();
    }

    final GlobalSearchScope scope = GlobalSearchScope.allScope(myModule.getProject());
    final List<Set<ResourceEntry>> values = FileBasedIndex.getInstance()
      .getValues(AndroidValueResourcesIndex.INDEX_ID, AndroidValueResourcesIndex.createTypeNameMarkerKey(resourceType, resourceName),
                 scope);

    final Set<VirtualFile> valueResourceFiles = getAllValueResourceFiles();
    final List<ValueResourceInfoImpl> result = new ArrayList<ValueResourceInfoImpl>();

    for (Set<ResourceEntry> entrySet : values) {
      for (ResourceEntry entry : entrySet) {
        final String name = entry.getName();

        if (AndroidUtils.equal(resourceName, name, distinguishDelimetersInName)) {
          final Collection<VirtualFile> files =
            FileBasedIndex.getInstance().getContainingFiles(AndroidValueResourcesIndex.INDEX_ID, entry, scope);

          for (VirtualFile file : files) {
            if (valueResourceFiles.contains(file)) {
              result.add(new ValueResourceInfoImpl(name, type, file, myModule));
            }
          }
        }
      }
    }
    return result;
  }
}
