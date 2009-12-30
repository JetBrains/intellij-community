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
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import com.intellij.util.xmlb.annotations.Attribute;
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
      if (value != null && dependencies.contains(value)) {
        registerExtensions(prefix, plugin, registrar, psiManager);
      }
    }

  }

  private static void registerExtensions(final String prefix, final IdeaPlugin plugin, final DomExtensionsRegistrar registrar,
                                         final PsiManager psiManager) {
    final String pluginId = plugin.getPluginId();
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
    domExtension.putUserData(DomExtension.KEY_DECLARATION, extensionPoint);
    domExtension.addExtender(new DomExtender() {
      public void registerExtensions(@NotNull final DomElement domElement, @NotNull final DomExtensionsRegistrar registrar) {
        final String interfaceName = extensionPoint.getInterface().getStringValue();
        if (interfaceName != null) {
          registrar.registerGenericAttributeValueChildExtension(new XmlName("implementation"), PsiClass.class).setConverter(
              CLASS_CONVERTER);

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
    final PsiAnnotation[] annotations = field.getModifierList().getAnnotations();
    final PsiConstantEvaluationHelper evalHelper = JavaPsiFacade.getInstance(field.getProject()).getConstantEvaluationHelper();
    for (PsiAnnotation annotation : annotations) {
      final String qName = annotation.getQualifiedName();
      if (qName != null) {
        if (qName.equals(Attribute.class.getName())) {
          final PsiAnnotationMemberValue attributeName = annotation.findAttributeValue("value");
          if (attributeName != null && attributeName instanceof PsiExpression) {
            final Class<String> type = String.class;
            PsiExpression expression = (PsiExpression)attributeName;
            final Object evaluatedExpression = evalHelper.computeConstantExpression(expression, false);
            if (evaluatedExpression != null) {
              registrar.registerGenericAttributeValueChildExtension(new XmlName(evaluatedExpression.toString()), type);
            }
          }
        } else if (qName.equals(Tag.class.getName())) {
          final PsiAnnotationMemberValue attributeName = annotation.findAttributeValue("value");
          if (attributeName != null && attributeName instanceof PsiExpression) {
            PsiExpression expression = (PsiExpression)attributeName;
            final Object evaluatedExpression = evalHelper.computeConstantExpression(expression, false);
            if (evaluatedExpression != null) {
              // I guess this actually needs something like registrar.registerGenericTagValueChildExtension...
              registrar.registerFixedNumberChildExtension(new XmlName(evaluatedExpression.toString()), SimpleTagValue.class);
            }
          }
        }
      }
    }
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
