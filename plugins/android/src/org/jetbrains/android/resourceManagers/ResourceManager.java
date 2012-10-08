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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
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

  public boolean processFileResources(@Nullable String resourceType, @NotNull FileResourceProcessor processor,
                                      boolean withDependencies) {
    return processFileResources(resourceType, processor, withDependencies, true);
  }

  public boolean processFileResources(@Nullable String resourceType, @NotNull FileResourceProcessor processor,
                                      boolean withDependencies, boolean publicOnly) {
    final VirtualFile[] resDirs = withDependencies ? getAllResourceDirs() : new VirtualFile[]{getResourceDir()};

    for (VirtualFile resSubdir : AndroidResourceUtil.getResourceSubdirs(resourceType, resDirs)) {
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
  public List<PsiFile> findResourceFiles(@NotNull final String resType,
                                         @Nullable final String resName,
                                         final boolean distinguishDelimetersInName,
                                         @NotNull String... extensions) {
    return findResourceFiles(resType, resName, distinguishDelimetersInName, true, extensions);
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull final String resType1,
                                         @Nullable final String resName1,
                                         final boolean distinguishDelimetersInName,
                                         final boolean withDependencies,
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
    }, withDependencies);
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

    index.processValues(AndroidValueResourcesIndex.INDEX_ID, typeMarkerEntry, null, new FileBasedIndex.ValueProcessor<Set<AndroidValueResourcesIndex.MyResourceInfo>>() {
      @Override
      public boolean process(VirtualFile file, Set<AndroidValueResourcesIndex.MyResourceInfo> infos) {
        for (AndroidValueResourcesIndex.MyResourceInfo info : infos) {
          Set<ResourceEntry> resourcesInFile = file2resourceSet.get(file);

          if (resourcesInFile == null) {
            resourcesInFile = new HashSet<ResourceEntry>();
            file2resourceSet.put(file, resourcesInFile);
          }
          resourcesInFile.add(info.getResourceEntry());
        }
        return true;
      }
    }, scope);

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
  public List<PsiElement> findIdDeclarations(@NotNull final String id) {
    if (!isResourcePublic(ResourceType.ID.getName(), id)) {
      return Collections.emptyList();
    }

    final List<PsiElement> declarations = new ArrayList<PsiElement>();
    final Collection<VirtualFile> files =
      FileBasedIndex.getInstance().getContainingFiles(AndroidIdIndex.INDEX_ID, id, GlobalSearchScope.allScope(myModule.getProject()));
    final Set<VirtualFile> fileSet = new HashSet<VirtualFile>(files);
    final PsiManager psiManager = PsiManager.getInstance(myModule.getProject());

    for (VirtualFile subdir : getResourceSubdirsToSearchIds()) {
      for (VirtualFile file : subdir.getChildren()) {
        if (fileSet.contains(file)) {
          final PsiFile psiFile = psiManager.findFile(file);

          if (psiFile instanceof XmlFile) {
            psiFile.accept(new XmlRecursiveElementVisitor() {
              @Override
              public void visitXmlAttributeValue(XmlAttributeValue attributeValue) {
                if (AndroidResourceUtil.isIdDeclaration(attributeValue)) {
                  final String idInAttr = AndroidResourceUtil.getResourceNameByReferenceText(attributeValue.getValue());

                  if (id.equals(idInAttr)) {
                    declarations.add(attributeValue);
                  }
                }
              }
            });
          }
        }
      }
    }
    return declarations;
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

    index.processValues(AndroidIdIndex.INDEX_ID, AndroidIdIndex.MARKER, null, new FileBasedIndex.ValueProcessor<Set<String>>() {
      @Override
      public boolean process(VirtualFile file, Set<String> value) {
        file2ids.put(file, value);
        return true;
      }
    }, scope);

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
    final List<ValueResourceInfoImpl> resources = findValueResourceInfos(resourceType, resourceName, distinguishDelimitersInName, false);
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
                                                            @NotNull final String resourceName,
                                                            final boolean distinguishDelimetersInName,
                                                            boolean searchAttrs) {
    final ResourceType type = ResourceType.getEnum(resourceType);
    if (type == null ||
        !AndroidResourceUtil.VALUE_RESOURCE_TYPES.contains(type) &&
        (type != ResourceType.ATTR || !searchAttrs)) {
      return Collections.emptyList();
    }
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myModule.getProject());
    final List<ValueResourceInfoImpl> result = new ArrayList<ValueResourceInfoImpl>();
    final Set<VirtualFile> valueResourceFiles = getAllValueResourceFiles();

    FileBasedIndex.getInstance()
      .processValues(AndroidValueResourcesIndex.INDEX_ID, AndroidValueResourcesIndex.createTypeNameMarkerKey(resourceType, resourceName),
                     null, new FileBasedIndex.ValueProcessor<Set<AndroidValueResourcesIndex.MyResourceInfo>>() {
      @Override
      public boolean process(VirtualFile file, Set<AndroidValueResourcesIndex.MyResourceInfo> infos) {
        for (AndroidValueResourcesIndex.MyResourceInfo info : infos) {
          final String name = info.getResourceEntry().getName();

          if (AndroidUtils.equal(resourceName, name, distinguishDelimetersInName)) {
            if (valueResourceFiles.contains(file)) {
              result.add(new ValueResourceInfoImpl(info.getResourceEntry().getName(), type, file, myModule, info.getOffset()));
            }
          }
        }
        return true;
      }
    }, scope);
    return result;
  }
}
