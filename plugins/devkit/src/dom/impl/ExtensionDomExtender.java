/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import com.intellij.util.xmlb.Constants;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author mike
 */
public class ExtensionDomExtender extends DomExtender<Extensions> {
  private static final PsiClassConverter CLASS_CONVERTER = new PluginPsiClassConverter();


  public void registerExtensions(@NotNull final Extensions extensions, @NotNull final DomExtensionsRegistrar registrar) {
    final XmlElement xmlElement = extensions.getXmlElement();
    if (xmlElement == null) return;
    final PsiManager psiManager = xmlElement.getManager();

    IdeaPlugin ideaPlugin = extensions.getParentOfType(IdeaPlugin.class, true);

    if (ideaPlugin == null) return;

    String prefix = extensions.getDefaultExtensionNs().getStringValue();
    if (prefix == null) prefix = extensions.getXmlns().getStringValue();
    if (prefix != null) {
      prefix += ".";
    } else {
      prefix = "";
    }

    registerExtensions(prefix, ideaPlugin, registrar, psiManager);
    final Collection<String> dependencies = getDependencies(ideaPlugin);
    for (IdeaPlugin plugin : IdeaPluginConverter.collectAllVisiblePlugins(DomUtil.getFile(extensions))) {
      final String value = plugin.getPluginId();
      // value == null for "included" platform plugins like DomPlugin.xml, XmlPlugin.xml, etc.
      if (value == null || dependencies.contains(value)) {
        registerExtensions(prefix, plugin, registrar, psiManager);
      }
    }

  }

  private static void registerExtensions(final String prefix, final IdeaPlugin plugin, final DomExtensionsRegistrar registrar,
                                         final PsiManager psiManager) {
    final String pluginId = StringUtil.notNullize(plugin.getPluginId(), "com.intellij");
    for (ExtensionPoints points : plugin.getExtensionPoints()) {
      for (ExtensionPoint point : points.getExtensionPoints()) {
        registerExtensionPoint(registrar, point, psiManager, prefix, pluginId);
      }
    }
  }

  private static void registerExtensionPoint(final DomExtensionsRegistrar registrar, final ExtensionPoint extensionPoint, final PsiManager manager, String prefix, @Nullable String pluginId) {
    String epName = extensionPoint.getName().getStringValue();
    if (epName != null && StringUtil.isNotEmpty(pluginId)) epName = pluginId + "." + epName;
    if (epName == null) epName = extensionPoint.getQualifiedName().getStringValue();
    if (epName == null) return;
    if (!epName.startsWith(prefix)) return;

    final DomExtension domExtension = registrar.registerCollectionChildrenExtension(new XmlName(epName.substring(prefix.length())), Extension.class);
    domExtension.setDeclaringElement(extensionPoint);
    domExtension.addExtender(new DomExtender() {
      public void registerExtensions(@NotNull final DomElement domElement, @NotNull final DomExtensionsRegistrar registrar) {
        final String interfaceName = extensionPoint.getInterface().getStringValue();
        if (interfaceName != null) {
          registrar.registerGenericAttributeValueChildExtension(new XmlName("implementation"), PsiClass.class).setConverter(CLASS_CONVERTER);

          final PsiClass implClass =
            JavaPsiFacade.getInstance(manager.getProject()).findClass(interfaceName, GlobalSearchScope.allScope(manager.getProject()));
          if (implClass != null) {
            registerXmlb(registrar, implClass);
          }
        }
        else {
          final String beanClassName = extensionPoint.getBeanClass().getStringValue();
          if (beanClassName != null) {
            final PsiClass beanClass =
              JavaPsiFacade.getInstance(manager.getProject()).findClass(beanClassName, GlobalSearchScope.allScope(manager.getProject()));

            if (beanClass != null) {
              registerXmlb(registrar, beanClass);
            }
          }
        }
      }
    });
  }

  private static void registerXmlb(final DomExtensionsRegistrar registrar, final PsiClass beanClass) {
    final PsiField[] fields = beanClass.getAllFields();
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.PUBLIC)) {
        registerField(registrar, field);
      }
    }
  }

  private static void registerField(final DomExtensionsRegistrar registrar, @NotNull final PsiField field) {
    final PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null) return;
    final String fieldName = field.getName();
    final PsiConstantEvaluationHelper evalHelper = JavaPsiFacade.getInstance(field.getProject()).getConstantEvaluationHelper();
    final PsiAnnotation attrAnno = modifierList.findAnnotation(Attribute.class.getName());
    if (attrAnno != null) {
      final String attrName = getStringAttribute(attrAnno, "value", evalHelper);
      if (attrName != null) {
        final DomExtension extension =
          registrar.registerGenericAttributeValueChildExtension(new XmlName(attrName), String.class);
        if (fieldName.endsWith("Class")) {
          extension.setConverter(CLASS_CONVERTER);
        }
      }
      return;
    }
    final PsiAnnotation tagAnno = modifierList.findAnnotation(Tag.class.getName());
    final PsiAnnotation propAnno = modifierList.findAnnotation(Property.class.getName());
    final PsiAnnotation absColAnno = modifierList.findAnnotation(AbstractCollection.class.getName());
    //final PsiAnnotation colAnno = modifierList.findAnnotation(Collection.class.getName()); // todo
    final String tagName = tagAnno != null? getStringAttribute(tagAnno, "value", evalHelper) :
                           propAnno != null && getBooleanAttribute(propAnno, "surroundWithTag", evalHelper)? Constants.OPTION : null;
    if (tagName != null) {
      if (absColAnno == null) {
        final DomExtension extension = registrar.registerFixedNumberChildExtension(new XmlName(tagName), SimpleTagValue.class);
        if (fieldName.endsWith("Class")) {
          extension.setConverter(CLASS_CONVERTER);
        }
      }
      else {
        registrar.registerFixedNumberChildExtension(new XmlName(tagName), DomElement.class).addExtender(new DomExtender() {
          @Override
          public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
            registerCollectionBinding(field.getType(), registrar, absColAnno, evalHelper);
          }
        });
      }
    }
    else if (absColAnno != null) {
      registerCollectionBinding(field.getType(), registrar, absColAnno, evalHelper);
    }
  }

  private static void registerCollectionBinding(PsiType type,
                                                DomExtensionsRegistrar registrar,
                                                PsiAnnotation anno,
                                                PsiConstantEvaluationHelper evalHelper) {
    final boolean surroundWithTag = getBooleanAttribute(anno, "surroundWithTag", evalHelper);
    if (surroundWithTag) return; // todo Set, List, Array
    final String tagName = getStringAttribute(anno, "elementTag", evalHelper);
    final String attrName = getStringAttribute(anno, "elementValueAttribute", evalHelper);
    final PsiClass psiClass = getElementType(type);
    if (tagName != null && attrName == null) {
      registrar.registerCollectionChildrenExtension(new XmlName(tagName), SimpleTagValue.class);
    }
    else if (tagName != null) {
      registrar.registerCollectionChildrenExtension(new XmlName(tagName), DomElement.class).addExtender(new DomExtender() {
        @Override
        public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
          registrar.registerGenericAttributeValueChildExtension(new XmlName(attrName), String.class);
        }
      });
    }
    else if (psiClass != null) {
      final PsiModifierList modifierList = psiClass.getModifierList();
      final PsiAnnotation tagAnno = modifierList == null? null : modifierList.findAnnotation(Tag.class.getName());
      final String classTagName = tagAnno == null? psiClass.getName() : getStringAttribute(tagAnno, "value", evalHelper);
      if (classTagName != null) {
        registrar.registerCollectionChildrenExtension(new XmlName(classTagName), DomElement.class).addExtender(new DomExtender() {
          @Override
          public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
            registerXmlb(registrar, psiClass);
          }
        });
      }
    }
  }

  @Nullable
  private static String getStringAttribute(final PsiAnnotation annotation,
                                           final String name,
                                           final PsiConstantEvaluationHelper evalHelper) {
    final Object o = evalHelper.computeConstantExpression(annotation.findAttributeValue(name), false);
    return o instanceof String && StringUtil.isNotEmpty((String)o)? (String)o : null;
  }

  private static boolean getBooleanAttribute(final PsiAnnotation annotation,
                                           final String name,
                                           final PsiConstantEvaluationHelper evalHelper) {
    final Object o = evalHelper.computeConstantExpression(annotation.findAttributeValue(name), false);
    return o instanceof Boolean? ((Boolean)o).booleanValue() : false;
  }

  @Nullable
  public static PsiClass getElementType(final PsiType psiType) {
    final PsiType elementType;
    if (psiType instanceof PsiArrayType) elementType = ((PsiArrayType)psiType).getComponentType();
    else if (psiType instanceof PsiClassType) {
      final PsiType[] types = ((PsiClassType)psiType).getParameters();
      elementType = types.length == 1? types[0] : null;
    }
    else elementType = null;
    return PsiTypesUtil.getPsiClass(elementType);
  }


  public static Collection<String> getDependencies(IdeaPlugin ideaPlugin) {
    Set<String> result = new HashSet<String>();

    result.add(PluginManager.CORE_PLUGIN_ID);

    for (Dependency dependency : ideaPlugin.getDependencies()) {
      ContainerUtil.addIfNotNull(dependency.getStringValue(), result);
    }

    if (ideaPlugin.getPluginId() == null) {
      final VirtualFile file = DomUtil.getFile(ideaPlugin).getOriginalFile().getVirtualFile();
      if (file != null) {
        final String fileName = file.getName();
        if (!"plugin.xml".equals(fileName)) {
          final VirtualFile mainPluginXml = file.findFileByRelativePath("../plugin.xml");
          if (mainPluginXml != null) {
            final PsiFile psiFile = PsiManager.getInstance(ideaPlugin.getManager().getProject()).findFile(mainPluginXml);
            if (psiFile instanceof XmlFile) {
              final XmlFile xmlFile = (XmlFile)psiFile;
              final DomFileElement<IdeaPlugin> fileElement = ideaPlugin.getManager().getFileElement(xmlFile, IdeaPlugin.class);
              if (fileElement != null) {
                final IdeaPlugin mainPlugin = fileElement.getRootElement();
                ContainerUtil.addIfNotNull(mainPlugin.getPluginId(), result);
                for (Dependency dependency : mainPlugin.getDependencies()) {
                  ContainerUtil.addIfNotNull(dependency.getStringValue(), result);
                }
              }
            }
          }
        }
      }
    }

    return result;
  }

  interface SimpleTagValue extends DomElement {
    @TagValue
    String getValue();
  }

}
