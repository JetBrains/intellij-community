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
import com.intellij.psi.util.PsiUtilBase;
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
import org.jetbrains.android.resourceManagers.ResourceManager;
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
    for (PsiElement element : psiElements) {
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
      PsiFile file = AndroidUtils.getFileTarget(element).getOriginalFile();
      String name = file.getName();
      PsiDirectory dir = file.getContainingDirectory();
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
          final String resType = AndroidResourceUtil.getResClassNameByValueResourceTag(facet, tag);
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
    else
      if (element instanceof PsiClass) {
        PsiClass c = (PsiClass)element;
        if (AndroidUtils.R_CLASS_NAME.equals(c.getName())) {
          PsiFile containingFile = element.getContainingFile();
            AndroidFacet facet = AndroidFacet.getInstance(containingFile);
            if (facet != null && AndroidUtils.isRClassFile(facet, containingFile)) {
              LocalResourceManager manager = facet.getLocalResourceManager();
              annotateRClass((PsiClass)element, result, manager);
          }
        }
      }
      else if (element instanceof XmlAttributeValue) {
        annotateXmlAttributeValue((XmlAttributeValue)element, result);
      }
      /*else if (element instanceof PsiReferenceExpression) {
        PsiElement targetElement = ((PsiReferenceExpression)element).resolve();
        if (targetElement instanceof PsiField) {
          PsiField targetField = (PsiField)targetElement;
          PsiFile file = targetField.getContainingFile();
          if (file != null && AndroidResourceUtil.isRJavaField(file, targetField)) {
            annotateElementNavToResource(element, targetField, LocalResourceManager.getInstance(containingFile), result, null, true);
          }
        }
      }*/

  }

  @NotNull
  private static Map<MyResourceEntry, List<PsiElement>> buildLocalResourceMap(@NotNull Project project,
                                                                              @NotNull final LocalResourceManager resManager) {
    final Map<MyResourceEntry, List<PsiElement>> result = new HashMap<MyResourceEntry, List<PsiElement>>();
    Collection<Resources> resourceFiles = resManager.getResourceElements();
    for (Resources res : resourceFiles) {
      for (String valueResourceType : ResourceManager.VALUE_RESOURCE_TYPES) {
        for (ResourceElement valueResource : ResourceManager.getValueResources(valueResourceType, res)) {
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
          String resType = ResourceManager.getResourceTypeByDirName(dir.getName());
          if (resType != null) {
            for (VirtualFile resourceFile : dir.getChildren()) {
              if (!resourceFile.isDirectory()) {
                PsiFile resourcePsiFile = psiManager.findFile(resourceFile);
                if (resourcePsiFile != null) {
                  String resName = ResourceManager.getResourceName(resType, resourceFile.getName());
                  if (resName != null) {
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
                AndroidResourceUtil.collectIdDeclarations(fieldName, manager.getModule(), targets);
              }
              List<PsiElement> resources = resourceMap.get(new MyResourceEntry(fieldName, resType));
              if (resources != null) {
                targets.addAll(resources);
              }
            }
            else {
              targets = AndroidResourceUtil.findResourcesByField(manager, resField);
            }
            return PsiUtilBase.toPsiElementArray(targets);
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

      if (!ResourceManager.equal(myName, that.myName, false)) return false;
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
