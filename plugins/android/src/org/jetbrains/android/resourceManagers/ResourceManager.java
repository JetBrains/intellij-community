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
import com.android.sdklib.SdkConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.DomElement;
import org.jetbrains.android.AndroidIdIndex;
import org.jetbrains.android.AndroidValueResourcesIndex;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.resources.Item;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.facet.AndroidFacet;
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

  public static final Set<String> REFERABLE_RESOURCE_TYPES = new HashSet<String>();
  public static final String[] FILE_RESOURCE_TYPES = new String[]{"drawable", "anim", "layout", "values", "menu", "xml", "raw", "color"};
  public static final String[] VALUE_RESOURCE_TYPES =
    new String[]{"drawable", "dimen", "color", "string", "style", "array", "id", "bool", "integer", "integer-array"};
  private static final String[] DRAWABLE_EXTENSIONS = new String[]{AndroidUtils.PNG_EXTENSION, "jpg", "gif"};

  protected final Module myModule;

  static {
    addAll(REFERABLE_RESOURCE_TYPES, FILE_RESOURCE_TYPES);
    addAll(REFERABLE_RESOURCE_TYPES, VALUE_RESOURCE_TYPES);
    REFERABLE_RESOURCE_TYPES.remove("values");
  }

  protected ResourceManager(@NotNull Module module) {
    myModule = module;
  }

  public Module getModule() {
    return myModule;
  }

  @Nullable
  public static String getDefaultResourceFileName(@NotNull String resourceType) {
    if (ArrayUtil.find(VALUE_RESOURCE_TYPES, resourceType) < 0) {
      return null;
    }
    return resourceType + "s.xml";
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
  public static String getResourceName(@NotNull String resourceType, @NotNull String fileName) {
    String extension = FileUtil.getExtension(fileName);
    String s = FileUtil.getNameWithoutExtension(fileName);
    if (resourceType.equals("drawable") && ArrayUtil.find(DRAWABLE_EXTENSIONS, extension) >= 0) {
      if (s.endsWith(".9") && extension.equals(AndroidUtils.PNG_EXTENSION)) {
        return s.substring(0, s.length() - 2);
      }
      return s;
    }
    return s;
  }

  private static boolean isCorrectFileName(@NotNull String resourceType, @NotNull String fileName) {
    return getResourceName(resourceType, fileName) != null;
  }

  public static boolean equal(@Nullable String s1, @Nullable String s2, boolean distinguishDelimeters) {
    if (s1 == null || s2 == null) {
      return false;
    }
    if (s1.length() != s2.length()) return false;
    for (int i = 0, n = s1.length(); i < n; i++) {
      char c1 = s1.charAt(i);
      char c2 = s2.charAt(i);
      if (distinguishDelimeters || (Character.isLetterOrDigit(c1) && Character.isLetterOrDigit(c2))) {
        if (c1 != c2) return false;
      }
    }
    return true;
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
          String s = getResourceName(resType, resFile.getName());
          if (resName == null || equal(resName, s, distinguishDelimetersInName)) {
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
          result.addAll(getValueResourcesFromElement(resourceType, resources));
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
        String type = AndroidResourceUtil.getResourceTypeByDirName(dir.getName());
        if (type == null) return null;
        return isCorrectFileName(type, file.getName()) ? type : null;
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
        String resName = getResourceName(resourceType, resourceFile.getName());
        if (resName != null) result.add(resName);
      }
    }
    return result;
  }

  @NotNull
  public Collection<String> getValueResourceNames(@NotNull final String resourceType) {
    final ResourceType type = ResourceType.getEnum(resourceType);

    if (type == null) {
      LOG.error("Unknown resource type " + resourceType);
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

  @NotNull
  public static List<ResourceElement> getValueResourcesFromElement(@NotNull String resourceType, Resources resources) {
    List<ResourceElement> result = new ArrayList<ResourceElement>();
    if (resourceType.equals("string")) {
      result.addAll(resources.getStrings());
    }
    else if (resourceType.equals("drawable")) {
      result.addAll(resources.getDrawables());
    }
    else if (resourceType.equals("color")) {
      result.addAll(resources.getColors());
    }
    else if (resourceType.equals("dimen")) {
      result.addAll(resources.getDimens());
    }
    else if (resourceType.equals("style")) {
      result.addAll(resources.getStyles());
    }
    else if (resourceType.equals("array")) {
      result.addAll(resources.getStringArrays());
      result.addAll(resources.getIntegerArrays());
      result.addAll(resources.getArrays());
    }
    else if (resourceType.equals("integer")) {
      result.addAll(resources.getIntegers());
    }
    else if (resourceType.equals("bool")) {
      result.addAll(resources.getBools());
    }
    for (Item item : resources.getItems()) {
      String type = item.getType().getValue();
      if (resourceType.equals(type)) {
        result.add(item);
      }
    }
    return result;
  }

  @Nullable
  public abstract AttributeDefinitions getAttributeDefinitions();

  // searches only declarations such as "@+id/..."
  @Nullable
  public List<PsiElement> findIdDeclarations(@NotNull String id) {
    List<PsiElement> declarations = new ArrayList<PsiElement>();
    collectIdDeclarations(id, declarations);
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
  protected List<VirtualFile> getResourceSubdirsToSearchIds() {
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
      LOG.error("Unknown resource type " + resourceType);
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

      if (equal(resourceName, name, distinguishDelimetersInName)) {
        result.add(element);
      }
    }
    return result;
  }

  public static boolean isInResourceSubdirectory(@NotNull PsiFile file, @Nullable String resourceType) {
    file = file.getOriginalFile();
    PsiDirectory dir = file.getContainingDirectory();
    if (dir == null) return false;
    return isResourceSubdirectory(dir, resourceType);
  }

  public static boolean isResourceSubdirectory(PsiDirectory dir, String resourceType) {
    if (resourceType != null && !dir.getName().startsWith(resourceType)) return false;
    dir = dir.getParent();
    if (dir == null) return false;
    if ("default".equals(dir.getName())) {
      dir = dir.getParentDirectory();
    }
    return dir != null && isResourceDirectory(dir);
  }

  public static boolean isResourceDirectory(VirtualFile dir, Project project) {
    Module module = ModuleUtil.findModuleForFile(dir, project);
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      return facet != null && facet.getLocalResourceManager().isResourceDir(dir);
    }
    return false;
  }

  public static boolean isResourceDirectory(PsiDirectory dir) {
    // check facet settings
    VirtualFile vf = dir.getVirtualFile();

    if (isResourceDirectory(vf, dir.getProject())) {
      return true;
    }

    // method can be invoked for system resource dir, so we should check it
    if (!SdkConstants.FD_RES.equals(dir.getName())) return false;
    dir = dir.getParent();
    if (dir != null) {
      if (dir.findFile(SdkConstants.FN_ANDROID_MANIFEST_XML) != null) {
        return true;
      }
      dir = dir.getParent();
      if (dir != null) {
        if (containsAndroidJar(dir)) return true;
        dir = dir.getParent();
        if (dir != null) {
          return containsAndroidJar(dir);
        }
      }
    }
    return false;
  }

  private static boolean containsAndroidJar(@NotNull PsiDirectory psiDirectory) {
    return psiDirectory.findFile(SdkConstants.FN_FRAMEWORK_LIBRARY) != null;
  }

  public void collectIdDeclarations(@NotNull final String id, final List<PsiElement> targets) {
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
                    targets.add(attributeValue);
                  }
                }
              }
            });
          }
        }
      }
    }
  }
}
