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

package org.jetbrains.android.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModulePackageIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceUtil {
  public static final String NEW_ID_PREFIX = "@+id/";

  private AndroidResourceUtil() {
  }

  @NotNull
  public static PsiField[] findResourceFields(@NotNull AndroidFacet facet,
                                              @NotNull String resClassName,
                                              @NotNull String resourceName,
                                              boolean onlyInOwnPackages) {
    resourceName = getRJavaFieldName(resourceName);
    List<PsiField> result = findResourceFieldsByName(facet, resClassName, resourceName, onlyInOwnPackages);
    return result.toArray(new PsiField[result.size()]);
  }

  public static List<PsiField> findResourceFieldsByName(AndroidFacet facet,
                                                        String innerClassName,
                                                        String fieldName,
                                                        boolean onlyInOwnPackages) {
    List<PsiJavaFile> rClassFiles = findRClassFiles(facet, onlyInOwnPackages);
    List<PsiField> result = new ArrayList<PsiField>();
    for (PsiJavaFile rClassFile : rClassFiles) {
      if (rClassFile == null) continue;
      PsiClass rClass = findClass(rClassFile.getClasses(), AndroidUtils.R_CLASS_NAME);
      if (rClass != null) {
        PsiClass resourceTypeClass = findClass(rClass.getInnerClasses(), innerClassName);
        if (resourceTypeClass != null) {
          PsiField field = resourceTypeClass.findFieldByName(fieldName, false);
          if (field != null) {
            result.add(field);
          }
        }
      }
    }
    return result;
  }

  @NotNull
  public static List<PsiJavaFile> findRClassFiles(@NotNull AndroidFacet facet, boolean onlyInOwnPackages) {
    Module module = facet.getModule();
    final Project project = module.getProject();
    Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return Collections.emptyList();
    }
    final Set<PsiDirectory> dirs = new HashSet<PsiDirectory>();
    collectDirsForPackage(module, project, null, dirs, new HashSet<Module>(), onlyInOwnPackages);
    List<PsiJavaFile> rJavaFiles = new ArrayList<PsiJavaFile>();
    for (PsiDirectory dir : dirs) {
      VirtualFile file = dir.getVirtualFile().findChild(AndroidCommonUtils.R_JAVA_FILENAME);
      if (file != null) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile instanceof PsiJavaFile) {
          rJavaFiles.add((PsiJavaFile)psiFile);
        }
      }
    }
    return rJavaFiles;
  }

  private static void collectDirsForPackage(Module module,
                                            final Project project,
                                            @Nullable String packageName,
                                            final Set<PsiDirectory> dirs,
                                            Set<Module> visitedModules,
                                            boolean onlyInOwnPackages) {
    if (!visitedModules.add(module)) return;
    if (packageName != null) {
      ModulePackageIndex.getInstance(module).getDirsByPackageName(packageName, false).forEach(new Processor<VirtualFile>() {
        public boolean process(final VirtualFile directory) {
          dirs.add(PsiManager.getInstance(project).findDirectory(directory));
          return true;
        }
      });
    }
    AndroidFacet ownFacet = AndroidFacet.getInstance(module);
    String ownPackageName = null;
    if (ownFacet != null) {
      Manifest ownManifest = ownFacet.getManifest();
      ownPackageName = ownManifest != null ? ownManifest.getPackage().getValue() : null;
      if (ownPackageName != null && !ownPackageName.equals(packageName)) {
        ModulePackageIndex.getInstance(module).getDirsByPackageName(ownPackageName, false).forEach(new Processor<VirtualFile>() {
          public boolean process(final VirtualFile directory) {
            dirs.add(PsiManager.getInstance(project).findDirectory(directory));
            return true;
          }
        });
      }
    }
    for (Module otherModule : ModuleManager.getInstance(project).getModules()) {
      if (ModuleRootManager.getInstance(otherModule).isDependsOn(module)) {
        collectDirsForPackage(otherModule, project, packageName != null || onlyInOwnPackages ? packageName : ownPackageName, dirs,
                              visitedModules, onlyInOwnPackages);
      }
    }
  }

  @Nullable
  public static PsiClass findClass(PsiClass[] classes, @NotNull String name) {
    for (PsiClass c : classes) {
      if (name.equals(c.getName())) {
        return c;
      }
    }
    return null;
  }

  @NotNull
  public static PsiField[] findResourceFieldsForFileResource(PsiFile file, boolean onlyInOwnPackages) {
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet != null) {
      LocalResourceManager manager = facet.getLocalResourceManager();
      String resourceType = manager.getFileResourceType(file);
      if (resourceType != null) {
        String resourceName = ResourceManager.getResourceName(resourceType, file.getName());
        return findResourceFields(facet, resourceType, resourceName, onlyInOwnPackages);
      }
    }
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public static String getRJavaFieldName(@NotNull String resourceName) {
    String[] identifiers = resourceName.split("\\.");
    StringBuilder result = new StringBuilder();
    for (int i = 0, n = identifiers.length; i < n; i++) {
      result.append(identifiers[i]);
      if (i < n - 1) {
        result.append('_');
      }
    }
    return result.toString();
  }

  public static boolean isCorrectAndroidResourceName(@NotNull String resourceName) {
    if (resourceName.length() == 0) return false;
    String[] identifiers = resourceName.split("\\.");
    for (String identifier : identifiers) {
      if (!StringUtil.isJavaIdentifier(identifier)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public static PsiField[] findResourceFieldsForValueResource(XmlTag tag, boolean onlyInOwnPackages) {
    AndroidFacet facet = AndroidFacet.getInstance(tag);
    if (facet != null) {
      String resourceType = getResClassNameByValueResourceTag(facet, tag);
      if (resourceType != null) {
        String name = tag.getAttributeValue("name");
        if (name != null) {
          return findResourceFields(facet, resourceType, name, onlyInOwnPackages);
        }
      }
    }
    return PsiField.EMPTY_ARRAY;
  }

  @Nullable
  public static String getResClassNameByValueResourceTag(AndroidFacet facet, XmlTag tag) {
    LocalResourceManager manager = facet.getLocalResourceManager();
    String fileResType = manager.getFileResourceType(tag.getContainingFile());
    if ("values".equals(fileResType)) {
      return getResourceTypeByValueResourceTag(tag);
    }
    return null;
  }

  @Nullable
  public static String getResourceTypeByValueResourceTag(XmlTag tag) {
    String resClassName = tag.getName();
    resClassName = resClassName.equals("item")
                   ? tag.getAttributeValue("type", null)
                   : getResourceTypeByTagName(resClassName);
    if (resClassName != null) {
      final String resourceName = tag.getAttributeValue("name");
      return resourceName != null ? resClassName : null;
    }
    return null;
  }

  public static String getResourceTypeByTagName(@NotNull String tagName) {
    if (tagName.equals("declare-styleable")) {
      tagName = "styleable";
    }
    else if (tagName.endsWith("-array")) {
      tagName = "array";
    }
    return tagName;
  }

  @Nullable
  public static String getResourceClassName(@NotNull PsiField field) {
    PsiClass resourceClass = field.getContainingClass();
    if (resourceClass != null) {
      PsiClass parentClass = resourceClass.getContainingClass();
      if (parentClass != null) {
        if (AndroidUtils.R_CLASS_NAME.equals(parentClass.getName()) && parentClass.getContainingClass() == null) {
          return resourceClass.getName();
        }
      }
    }
    return null;
  }

  // result contains XmlAttributeValue or PsiFile

  @NotNull
  public static List<PsiElement> findResourcesByField(@NotNull PsiField field) {
    LocalResourceManager manager = LocalResourceManager.getInstance(field);
    if (manager != null) {
      return findResourcesByField(manager, field);
    }
    return Collections.emptyList();
  }

  @NotNull
  public static List<PsiElement> findResourcesByField(@NotNull LocalResourceManager manager,
                                                      @NotNull PsiField field) {
    String type = getResourceClassName(field);
    List<PsiElement> targets = new ArrayList<PsiElement>();
    if (type != null) {
      String name = field.getName();
      if (type.equals("id")) {
        manager.collectIdDeclarations(name, targets);
      }
      for (PsiFile file : manager.findResourceFiles(type, name, false)) {
        targets.add(file);
      }
      for (ResourceElement element : manager.findValueResources(type, name, false)) {
        targets.add(element.getName().getXmlAttributeValue());
      }
      if (type.equals("attr")) {
        for (Attr attr : manager.findAttrs(name)) {
          targets.add(attr.getName().getXmlAttributeValue());
        }
      }
      else if (type.equals("styleable")) {
        for (DeclareStyleable styleable : manager.findStyleables(name)) {
          targets.add(styleable.getName().getXmlAttributeValue());
        }
      }
    }
    return targets;
  }

  public static boolean isResourceField(@NotNull PsiField field) {
    PsiClass c = field.getContainingClass();
    if (c == null) return false;
    c = c.getContainingClass();
    if (c != null && AndroidUtils.R_CLASS_NAME.equals(c.getName())) {
      AndroidFacet facet = AndroidFacet.getInstance(field);
      if (facet != null) {
        PsiFile file = c.getContainingFile();
        if (file != null && AndroidUtils.isRClassFile(facet, file)) {
          return true;
        }
      }
    }
    return false;
  }


  @NotNull
  public static PsiField[] findIdFields(XmlAttributeValue value) {
    if (value.getParent() instanceof XmlAttribute) {
      return findIdFields((XmlAttribute)value.getParent());
    }
    return PsiField.EMPTY_ARRAY;
  }

  public static boolean isIdDeclaration(String attrValue) {
    return attrValue != null && attrValue.startsWith(NEW_ID_PREFIX);
  }

  public static boolean isIdReference(String attrValue) {
    return attrValue != null && attrValue.startsWith("@id/");
  }

  public static boolean isIdDeclaration(XmlAttributeValue value) {
    String s = value.getValue();
    return isIdDeclaration(s);
  }

  public static boolean isIdDeclaration(XmlAttribute attribute) {
    XmlAttributeValue value = attribute.getValueElement();
    return value != null && isIdDeclaration(value);
  }

  @NotNull
  public static PsiField[] findIdFields(XmlAttribute attribute) {
    if (isIdDeclaration(attribute)) {
      String id = getResourceNameByReferenceText(attribute.getValue());
      if (id != null) {
        AndroidFacet facet = AndroidFacet.getInstance(attribute);
        if (facet != null) {
          return findResourceFields(facet, "id", id, false);
        }
      }
    }
    return PsiField.EMPTY_ARRAY;
  }

  @Nullable
  public static String getResourceNameByReferenceText(@NotNull String text) {
    int i = text.indexOf('/');
    if (i < text.length() - 1) {
      return text.substring(i + 1, text.length());
    }
    return null;
  }

  public static boolean isRJavaField(@NotNull PsiFile file, @NotNull PsiField field) {
    PsiClass aClass = field.getContainingClass();
    if (aClass != null) {
      aClass = aClass.getContainingClass();
      if (aClass != null && AndroidUtils.R_CLASS_NAME.equals(aClass.getName())) {
        AndroidFacet facet = AndroidFacet.getInstance(file);
        if (facet != null) {
          return AndroidUtils.isRClassFile(facet, file);
        }
      }
    }
    return false;
  }

  @NotNull
  public static PsiElement[] findResources(@NotNull PsiField resField) {
    AndroidFacet facet = AndroidFacet.getInstance(resField);
    assert facet != null;
    LocalResourceManager manager = facet.getLocalResourceManager();
    List<PsiElement> targets = findResourcesByField(manager, resField);
    return PsiUtilCore.toPsiElementArray(targets);
  }

  @NotNull
  public static ResourceElement addValueResource(@NotNull final String type, @NotNull final Resources resources) {
    if (type.equals("string")) {
      return resources.addString();
    }
    else if (type.equals("dimen")) {
      return resources.addDimen();
    }
    else if (type.equals("color")) {
      return resources.addColor();
    }
    else if (type.equals("drawable")) {
      return resources.addDrawable();
    }
    else if (type.equals("style")) {
      return resources.addStyle();
    }
    else if (type.equals("array")) {
      // todo: choose among string-array, integer-array and array
      return resources.addStringArray();
    }
    else if (type.equals("integer")) {
      return resources.addInteger();
    }
    else if (type.equals("bool")) {
      return resources.addBool();
    }
    else if (type.equals("id")) {
      Item item = resources.addItem();
      item.getType().setValue("id");
      return item;
    }
    throw new IllegalArgumentException("Incorrect resource type");
  }

  @Nullable
  public static String getResourceTypeByDirName(@NotNull String name) {
    int index = name.indexOf('-');
    String type = index >= 0 ? name.substring(0, index) : name;
    return ArrayUtil.find(ResourceManager.FILE_RESOURCE_TYPES, type) >= 0 ? type : null;
  }

  @NotNull
  public static List<VirtualFile> getResourceSubdirs(@Nullable String resourceType, @NotNull VirtualFile[] resourceDirs) {
    List<VirtualFile> dirs = new ArrayList<VirtualFile>();
    if (ArrayUtil.find(ResourceManager.FILE_RESOURCE_TYPES, resourceType) < 0 && resourceType != null) {
      return dirs;
    }
    for (VirtualFile resourcesDir : resourceDirs) {
      if (resourcesDir == null) return dirs;
      if (resourceType == null) {
        ContainerUtil.addAll(dirs, resourcesDir.getChildren());
      }
      else {
        for (VirtualFile child : resourcesDir.getChildren()) {
          String type = getResourceTypeByDirName(child.getName());
          if (resourceType.equals(type)) dirs.add(child);
        }
      }
    }
    return dirs;
  }
}
