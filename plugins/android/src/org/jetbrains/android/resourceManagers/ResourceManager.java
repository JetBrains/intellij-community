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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
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
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.resourceManagers.LocalResourceManager");

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

  public boolean isResourceDir(@NotNull VirtualFile dir) {
    return dir.equals(getResourceDir());
  }

  @NotNull
  public VirtualFile[] getResourceOverlayDirs() {
    return VirtualFile.EMPTY_ARRAY;
  }

  @NotNull
  public List<VirtualFile> getResourceSubdirs(@Nullable String resourceType) {
    return AndroidResourceUtil.getResourceSubdirs(resourceType, getAllResourceDirs());
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull String resType,
                                         @Nullable String resName,
                                         boolean distinguishDelimetersInName,
                                         @NotNull String... extensions) {
    List<PsiFile> result = new ArrayList<PsiFile>();
    Set<String> extensionSet = new HashSet<String>();
    addAll(extensionSet, extensions);
    for (VirtualFile dir : getResourceSubdirs(resType)) {
      for (final VirtualFile resFile : dir.getChildren()) {
        String extension = resFile.getExtension();
        if (extensions.length == 0 || extensionSet.contains(extension)) {
          String s = AndroidCommonUtils.getResourceName(resType, resFile.getName());
          if (resName == null || AndroidUtils.equal(resName, s, distinguishDelimetersInName)) {
            PsiFile file = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
              @Nullable
              public PsiFile compute() {
                return PsiManager.getInstance(myModule.getProject()).findFile(resFile);
              }
            });
            if (file != null) {
              result.add(file);
            }
          }
        }
      }
    }
    return result;
  }

  public List<PsiFile> findResourceFiles(@NotNull String resType, @NotNull String resName, @NotNull String... extensions) {
    return findResourceFiles(resType, resName, true, extensions);
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull String resType) {
    return findResourceFiles(resType, null, true);
  }

  protected List<Resources> getResourceElements(@Nullable Set<VirtualFile> files) {
    return getRootDomElements(Resources.class, files);
  }

  private <T extends DomElement> List<T> getRootDomElements(@NotNull Class<T> elementType,
                                                            @Nullable Set<VirtualFile> files) {
    final List<T> result = new ArrayList<T>();
    for (VirtualFile file : getAllValueResourceFiles()) {
      if ((files == null || files.contains(file)) && file.isValid()) {
        T element = AndroidUtils.loadDomElement(myModule, file, elementType);
        if (element != null) result.add(element);
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
    Collection<Resources> resourceFiles = getResourceElements(files);
    for (final Resources resources : resourceFiles) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          if (!resources.isValid() || myModule.isDisposed() || myModule.getProject().isDisposed()) {
            return;
          }
          result.addAll(AndroidResourceUtil.getValueResourcesFromElement(resourceType, resources));
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
  public Set<String> getFileResourcesNames(@NotNull String resourceType) {
    Set<String> result = new HashSet<String>();
    List<VirtualFile> dirs = getResourceSubdirs(resourceType);
    for (VirtualFile dir : dirs) {
      for (VirtualFile resourceFile : dir.getChildren()) {
        if (resourceFile.isDirectory()) continue;
        result.add(AndroidCommonUtils.getResourceName(resourceType, resourceFile.getName()));
      }
    }
    return result;
  }

  @NotNull
  public Collection<String> getValueResourceNames(@NotNull final String resourceType) {
    final ResourceType type = ResourceType.getEnum(resourceType);

    if (type == null) {
      return Collections.emptyList();
    }

    final FileBasedIndex index = FileBasedIndex.getInstance();
    final ResourceEntry typeMarkerEntry = AndroidValueResourcesIndex.createTypeMarkerEntry(resourceType);
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
    final Set<String> result = new HashSet<String>();

    for (VirtualFile file : getAllValueResourceFiles()) {
      final Set<ResourceEntry> entries = file2resourceSet.get(file);

      if (entries != null) {
        for (ResourceEntry entry : entries) {
          result.add(entry.getName());
        }
      }
    }
    return result;
  }

  @Nullable
  public abstract AttributeDefinitions getAttributeDefinitions();

  // searches only declarations such as "@+id/..."
  @Nullable
  public List<PsiElement> findIdDeclarations(@NotNull final String id) {
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
          result.addAll(ids);
        }
      }
    }
    return result;
  }

  @NotNull
  private List<VirtualFile> getResourceSubdirsToSearchIds() {
    final List<VirtualFile> resSubdirs = new ArrayList<VirtualFile>();
    resSubdirs.addAll(getResourceSubdirs(ResourceType.LAYOUT.getName()));
    resSubdirs.addAll(getResourceSubdirs(ResourceType.MENU.getName()));
    return resSubdirs;
  }

  public List<ResourceElement> findValueResources(@NotNull String resType, @NotNull String resName) {
    return findValueResources(resType, resName, true);
  }

  @NotNull
  public List<ResourceElement> findValueResources(@NotNull String resourceType,
                                                  @NotNull String resourceName,
                                                  boolean distinguishDelimetersInName) {
    final ResourceType type = ResourceType.getEnum(resourceType);
    if (type == null) {
      return Collections.emptyList();
    }

    final Collection<VirtualFile> files = FileBasedIndex.getInstance()
      .getContainingFiles(AndroidValueResourcesIndex.INDEX_ID, new ResourceEntry(resourceType, resourceName),
                          GlobalSearchScope.allScope(myModule.getProject()));

    if (files.size() == 0) {
      return Collections.emptyList();
    }
    final Set<VirtualFile> fileSet = new HashSet<VirtualFile>(files);
    final List<ResourceElement> result = new ArrayList<ResourceElement>();

    for (ResourceElement element : getValueResources(resourceType, fileSet)) {
      final String name = element.getName().getValue();

      if (AndroidUtils.equal(resourceName, name, distinguishDelimetersInName)) {
        result.add(element);
      }
    }
    return result;
  }
}
