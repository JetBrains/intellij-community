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
package org.jetbrains.android;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ConstantFunction;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.DeclareStyleable;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author coyote
 */
public class AndroidResourcesLineMarkerProvider implements LineMarkerProvider {
  private static final Icon ICON = IconLoader.getIcon("/icons/navigate.png");

  public LineMarkerInfo getLineMarkerInfo(PsiElement psiElement) {
    return null;
  }

  public void collectSlowLineMarkers(List<PsiElement> psiElements, Collection<LineMarkerInfo> lineMarkerInfos) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < psiElements.size(); i++) {
      PsiElement element = psiElements.get(i);
      addMarkerInfo(element, lineMarkerInfos);
    }
  }

  @NotNull
  private static String getToolTip(@NotNull PsiElement element) {
    String s = "Go to ";
    if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      PsiClass resClass = field.getContainingClass();
      assert resClass != null;
      PsiClass rClass = resClass.getContainingClass();
      assert rClass != null;
      return s + rClass.getName() + '.' + resClass.getName() + '.' + field.getName();
    }
    else {
      final PsiFile file = AndroidUtils.getContainingFile(element);
      if (file == null) {
        return s;
      }
      final PsiFile originalFile = file.getOriginalFile();
      String name = originalFile.getName();
      PsiDirectory dir = originalFile.getContainingDirectory();
      if (dir == null) return s + name;
      return s + dir.getName() + '/' + name;
    }
  }

  private static LineMarkerInfo createLineMarkerInfo(@NotNull PsiElement element, @NotNull PsiElement... targets) {
    final String toolTip = targets.length == 1 ? getToolTip(targets[0]) : "Resource not found";
    return new LineMarkerInfo<PsiElement>(element,
                                          element.getTextOffset(),
                                          ICON,
                                          Pass.UPDATE_OVERRIDEN_MARKERS, 
                                          new ConstantFunction<PsiElement, String>(toolTip),
                                          new MyNavigationHandler(targets));
  }

  private static LineMarkerInfo createLazyLineMarkerInfo(@NotNull PsiElement element,
                                                         @NotNull final Computable<PsiElement[]> targetProvider) {
    return new LineMarkerInfo<PsiElement>(element,
                                          element.getTextOffset(),
                                          ICON,
                                          Pass.UPDATE_OVERRIDEN_MARKERS,
                                          new ConstantFunction<PsiElement, String>("Go to resource"),
                                          new MyLazyNavigationHandler(targetProvider));
  }

  private static void annotateXmlAttributeValue(@NotNull XmlAttributeValue attrValue, @NotNull Collection<LineMarkerInfo> result) {
    final AndroidFacet facet = AndroidFacet.getInstance(attrValue);
    if (facet != null) {
      PsiElement parent = attrValue.getParent();
      if (!(parent instanceof XmlAttribute)) return;
      final XmlAttribute attr = (XmlAttribute)parent;
      if (attr.getLocalName().equals("name")) {
        final XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
        if (tag != null) {
          String fileResType = facet.getLocalResourceManager().getFileResourceType(tag.getContainingFile());
          final String resType = "values".equals(fileResType) ? AndroidResourceUtil.getResourceTypeByValueResourceTag(tag) : null;
          if (resType != null) {
            result.add(createLazyLineMarkerInfo(tag, new Computable<PsiElement[]>() {
              @Override
              public PsiElement[] compute() {
                String name = tag.getAttributeValue("name");
                return name != null ? AndroidResourceUtil.findResourceFields(facet, resType, name, false) : PsiElement.EMPTY_ARRAY;
              }
            }));
          }
        }
      }
      else if (AndroidResourceUtil.isIdDeclaration(attrValue)) {
        result.add(createLazyLineMarkerInfo(attrValue, new Computable<PsiElement[]>() {
          @Override
          public PsiElement[] compute() {
            return AndroidResourceUtil.findIdFields(attr);
          }
        }));
      }
    }
  }

  private static void addMarkerInfo(@NotNull final PsiElement element, @NotNull Collection<LineMarkerInfo> result) {
    if (element instanceof PsiFile) {
      PsiField[] fields = AndroidResourceUtil.findResourceFieldsForFileResource((PsiFile)element, false);
      if (fields.length > 0) result.add(createLineMarkerInfo(element, fields));
    }
    else if (element instanceof PsiClass) {
      PsiClass c = (PsiClass)element;
      if (AndroidUtils.R_CLASS_NAME.equals(c.getName())) {
        PsiFile containingFile = element.getContainingFile();
        AndroidFacet facet = AndroidFacet.getInstance(containingFile);
        if (facet != null && AndroidResourceUtil.isRJavaFile(facet, containingFile)) {
          LocalResourceManager manager = facet.getLocalResourceManager();
          annotateRClass((PsiClass)element, result, manager);
        }
      }
    }
    else if (element instanceof XmlAttributeValue) {
      annotateXmlAttributeValue((XmlAttributeValue)element, result);
    }
  }

  @NotNull
  private static Map<MyResourceEntry, List<PsiElement>> buildLocalResourceMap(@NotNull Project project,
                                                                              @NotNull final LocalResourceManager resManager) {
    final Map<MyResourceEntry, List<PsiElement>> result = new HashMap<MyResourceEntry, List<PsiElement>>();
    Collection<Resources> resourceFiles = resManager.getResourceElements();
    for (Resources res : resourceFiles) {
      for (String valueResourceType : AndroidResourceUtil.VALUE_RESOURCE_TYPES) {
        for (ResourceElement valueResource : AndroidResourceUtil.getValueResourcesFromElement(valueResourceType, res)) {
          addResource(valueResourceType, valueResource, result);
        }
      }
      for (Attr attr : res.getAttrs()) {
        addResource("attr", attr, result);
      }
      for (DeclareStyleable styleable : res.getDeclareStyleables()) {
        addResource("styleable", styleable, result);
        for (Attr attr : styleable.getAttrs()) {
          addResource("attr", attr, result);
        }
      }
    }
    collectFileResources(project, resManager, result);
    return result;
  }

  private static void collectFileResources(Project project,
                                           final LocalResourceManager resManager,
                                           final Map<MyResourceEntry, List<PsiElement>> result) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        List<VirtualFile> resourceSubdirs = resManager.getResourceSubdirs(null);
        for (VirtualFile dir : resourceSubdirs) {
          String resType = AndroidCommonUtils.getResourceTypeByDirName(dir.getName());
          if (resType != null) {
            for (VirtualFile resourceFile : dir.getChildren()) {
              if (!resourceFile.isDirectory()) {
                PsiFile resourcePsiFile = psiManager.findFile(resourceFile);
                if (resourcePsiFile != null) {
                  String resName = AndroidCommonUtils.getResourceName(resType, resourceFile.getName());
                  MyResourceEntry key = new MyResourceEntry(resName, resType);
                  List<PsiElement> list = result.get(key);
                  if (list == null) {
                    list = new ArrayList<PsiElement>();
                    result.put(key, list);
                  }
                  list.add(resourcePsiFile);
                }
              }
            }
          }
        }
      }
    });
  }

  private static void addResource(String resType, ResourceElement resElement, Map<MyResourceEntry, List<PsiElement>> result) {
    GenericAttributeValue<String> nameValue = resElement.getName();
    if (nameValue != null) {
      String name = nameValue.getValue();
      if (name != null) {
        MyResourceEntry key = new MyResourceEntry(name, resType);
        List<PsiElement> list = result.get(key);
        if (list == null) {
          list = new ArrayList<PsiElement>();
          result.put(key, list);
        }
        list.add(nameValue.getXmlAttributeValue());
      }
    }
  }

  private static void annotateRClass(@NotNull PsiClass rClass,
                                     @NotNull Collection<LineMarkerInfo> result,
                                     @NotNull LocalResourceManager manager) {
    Map<MyResourceEntry, List<PsiElement>> resourceMap = buildLocalResourceMap(rClass.getProject(), manager);
    for (PsiClass c : rClass.getInnerClasses()) {
      for (PsiField field : c.getFields()) {
        annotateElementNavToResource(field, field, manager, result, resourceMap, false);
      }
    }
  }

  private static void annotateElementNavToResource(PsiElement element,
                                                   final PsiField resField,
                                                   final LocalResourceManager manager,
                                                   Collection<LineMarkerInfo> result,
                                                   @Nullable final Map<MyResourceEntry, List<PsiElement>> resourceMap,
                                                   boolean lazy) {
    final String fieldName = resField.getName();
    if (fieldName != null) {
      final String resType = AndroidResourceUtil.getResourceClassName(resField);
      if (resType != null) {
        Computable<PsiElement[]> targetProvider = new Computable<PsiElement[]>() {
          @Override
          public PsiElement[] compute() {
            final List<PsiElement> targets;
            if (resourceMap != null) {
              targets = new ArrayList<PsiElement>();
              if (resType.equals("id")) {
                targets.addAll(manager.findIdDeclarations(fieldName));
              }
              List<PsiElement> resources = resourceMap.get(new MyResourceEntry(fieldName, resType));
              if (resources != null) {
                targets.addAll(resources);
              }
            }
            else {
              targets = manager.findResourcesByField(resField);
            }
            return PsiUtilCore.toPsiElementArray(targets);
          }
        };
        if (lazy) {
          result.add(createLazyLineMarkerInfo(element, targetProvider));
        }
        else {
          PsiElement[] targets = targetProvider.compute();
          if (targets != null && targets.length > 0) {
            result.add(createLineMarkerInfo(element, targets));
          }
        }
      }
    }
  }

  static class MyResourceEntry {
    final String myName;
    final String myType;

    private MyResourceEntry(@NotNull String name, @NotNull String type) {
      myName = name;
      myType = type;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyResourceEntry that = (MyResourceEntry)o;

      if (!AndroidUtils.equal(myName, that.myName, false)) return false;
      if (!myType.equals(that.myType)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = 0;
      for (int i = 0; i < myName.length(); i++) {
        char c = myName.charAt(i);
        if (Character.isLetterOrDigit(c)) {
          result = 31 * result + (int)c;
        }
      }
      result = 31 * result + myType.hashCode();
      return result;
    }
  }

  public static class MyNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
    private final PsiElement[] myTargets;

    private MyNavigationHandler(@NotNull PsiElement[] targets) {
      myTargets = targets;
    }

    public void navigate(MouseEvent event, PsiElement psiElement) {
      AndroidUtils.navigateTo(myTargets, event != null ? new RelativePoint(event) : null);
    }

    public PsiElement[] getTargets() {
      return myTargets;
    }
  }

  public static class MyLazyNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
    private final Computable<PsiElement[]> myTargetProvider;

    private MyLazyNavigationHandler(Computable<PsiElement[]> targetProvider) {
      myTargetProvider = targetProvider;
    }

    @Override
    public void navigate(MouseEvent event, PsiElement psiElement) {
      PsiElement[] targets = myTargetProvider.compute();
      if (targets != null && targets.length > 0) {
        AndroidUtils.navigateTo(targets, event != null ? new RelativePoint(event) : null);
      }
    }

    public Computable<PsiElement[]> getTargetProvider() {
      return myTargetProvider;
    }
  }
}
