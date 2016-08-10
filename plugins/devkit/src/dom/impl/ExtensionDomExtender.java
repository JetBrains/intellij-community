/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.LinkedMultiMap;
import com.intellij.util.containers.MultiMap;
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

import java.lang.annotation.Annotation;
import java.util.*;

/**
 * @author mike
 */
public class ExtensionDomExtender extends DomExtender<Extensions> {
  private static final PsiClassConverter CLASS_CONVERTER = new PluginPsiClassConverter();
  private static final Converter LANGUAGE_CONVERTER = new LanguageResolvingConverter();
  private static final DomExtender EXTENSION_EXTENDER = new DomExtender() {
    public void registerExtensions(@NotNull final DomElement domElement, @NotNull final DomExtensionsRegistrar registrar) {
      final ExtensionPoint extensionPoint = (ExtensionPoint)domElement.getChildDescription().getDomDeclaration();
      assert extensionPoint != null;

      final String interfaceName = extensionPoint.getInterface().getStringValue();
      if (interfaceName != null) {
        final DomExtension implementationAttribute =
          registrar.registerGenericAttributeValueChildExtension(new XmlName("implementation"), PsiClass.class)
            .setConverter(CLASS_CONVERTER)
            .addCustomAnnotation(new MyExtendClass(interfaceName))
            .addCustomAnnotation(new MyRequired());

        final PsiClass interfaceClass = extensionPoint.getInterface().getValue();
        if (interfaceClass != null) {
          implementationAttribute.setDeclaringElement(interfaceClass);
        } else {
          implementationAttribute.setDeclaringElement(extensionPoint);
        }

        registerXmlb(registrar, interfaceClass, Collections.<With>emptyList());
      }
      else {
        final PsiClass beanClass = extensionPoint.getBeanClass().getValue();
        registerXmlb(registrar, beanClass, extensionPoint.getWithElements());
      }
    }
  };

  private static Set<IdeaPlugin> getVisiblePlugins(IdeaPlugin ideaPlugin) {
    Set<IdeaPlugin> result = ContainerUtil.newHashSet();
    MultiMap<String, IdeaPlugin> byId = getPluginMap(ideaPlugin.getManager().getProject());
    collectDependencies(ideaPlugin, result, byId);
    //noinspection NullableProblems
    result.addAll(byId.get(null));
    return result;
  }

  private static MultiMap<String, IdeaPlugin> getPluginMap(final Project project) {
    MultiMap<String, IdeaPlugin> byId = new LinkedMultiMap<>();
    for (IdeaPlugin each : IdeaPluginConverter.getAllPlugins(project)) {
      byId.putValue(each.getPluginId(), each);
    }
    return byId;
  }

  private static void collectDependencies(final IdeaPlugin ideaPlugin, Set<IdeaPlugin> result, final MultiMap<String, IdeaPlugin> byId) {
    if (!result.add(ideaPlugin)) {
      return;
    }

    for (String id : getDependencies(ideaPlugin)) {
      for (IdeaPlugin dep : byId.get(id)) {
        collectDependencies(dep, result, byId);
      }
    }
  }

  private static void registerExtensionPoint(final DomExtensionsRegistrar registrar,
                                             final ExtensionPoint extensionPoint,
                                             String epPrefix,
                                             @Nullable String pluginId) {
    String epName = extensionPoint.getName().getStringValue();
    if (epName != null && StringUtil.isNotEmpty(pluginId)) epName = pluginId + "." + epName;
    if (epName == null) epName = extensionPoint.getQualifiedName().getStringValue();
    if (epName == null) return;
    if (!epName.startsWith(epPrefix)) return;

    final DomExtension domExtension = registrar.registerCollectionChildrenExtension(new XmlName(epName.substring(epPrefix.length())), Extension.class);
    domExtension.setDeclaringElement(extensionPoint);
    domExtension.addExtender(EXTENSION_EXTENDER);
  }

  private static void registerXmlb(final DomExtensionsRegistrar registrar, @Nullable final PsiClass beanClass, @NotNull List<With> elements) {
    if (beanClass == null) return;

    for (PsiField field : beanClass.getAllFields()) {
      registerField(registrar, field, findWithElement(elements, field));
    }
  }

  @Nullable
  public static With findWithElement(List<With> elements, PsiField field) {
    for (With element : elements) {
      if (Comparing.equal(field.getName(), element.getAttribute().getStringValue())) {
        return element;
      }
    }
    return null;
  }

  private static void registerField(final DomExtensionsRegistrar registrar, @NotNull final PsiField field, With withElement) {
    final PsiMethod getter = PropertyUtil.findGetterForField(field);
    final PsiMethod setter = PropertyUtil.findSetterForField(field);
    if (!field.hasModifierProperty(PsiModifier.PUBLIC) && (getter == null || setter == null)) {
      return;
    }

    final String fieldName = field.getName();
    final PsiConstantEvaluationHelper evalHelper = JavaPsiFacade.getInstance(field.getProject()).getConstantEvaluationHelper();
    final PsiAnnotation attrAnno = findAnnotation(Attribute.class, field, getter, setter);
    if (attrAnno != null) {
      final String attrName = getStringAttribute(attrAnno, "value", evalHelper);
      if (attrName != null) {
        Class clazz = String.class;
        if (withElement != null || isClassField(fieldName)) {
          clazz = PsiClass.class;
        } else if (PsiType.BOOLEAN.equals(field.getType())) {
          clazz = Boolean.class;
        }
        final DomExtension extension =
          registrar.registerGenericAttributeValueChildExtension(new XmlName(attrName), clazz).setDeclaringElement(field);
        markAsClass(extension, fieldName, withElement);
        if (clazz.equals(String.class)) {
          markAsLanguage(extension, fieldName);
        }
      }
      return;
    }
    final PsiAnnotation tagAnno = findAnnotation(Tag.class, field, getter, setter);
    final PsiAnnotation propAnno = findAnnotation(Property.class, field, getter, setter);
    final PsiAnnotation absColAnno = findAnnotation(AbstractCollection.class, field, getter, setter);
    //final PsiAnnotation colAnno = modifierList.findAnnotation(Collection.class.getName()); // todo
    final String tagName = tagAnno != null? getStringAttribute(tagAnno, "value", evalHelper) :
                           propAnno != null && getBooleanAttribute(propAnno, "surroundWithTag", evalHelper)? Constants.OPTION : null;
    if (tagName != null) {
      if (absColAnno == null) {
        final DomExtension extension =
          registrar.registerFixedNumberChildExtension(new XmlName(tagName), SimpleTagValue.class).setDeclaringElement(field);
        markAsClass(extension, fieldName, withElement);
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

  private static void markAsLanguage(DomExtension extension, String fieldName) {
    if ("language".equals(fieldName)) {
      extension.setConverter(LANGUAGE_CONVERTER);
    }
  }

  private static void markAsClass(DomExtension extension, String fieldName, @Nullable With withElement) {
    if (withElement != null) {
      final String withClassName = withElement.getImplements().getStringValue();
      extension.addCustomAnnotation(new ExtendClassImpl() {
        @Override
        public String value() {
          return withClassName;
        }
      });
    }
    if (withElement != null || isClassField(fieldName)) {
      extension.setConverter(CLASS_CONVERTER);
    }
  }

  public static boolean isClassField(String fieldName) {
    return (fieldName.endsWith("Class") && !fieldName.equals("forClass")) || 
           fieldName.equals("implementation") || 
           fieldName.equals("serviceInterface") || 
           fieldName.equals("serviceImplementation");
  }

  @Nullable
  static PsiAnnotation findAnnotation(final Class<?> annotationClass, PsiMember... members) {
    for (PsiMember member : members) {
      if (member != null) {
        final PsiModifierList modifierList = member.getModifierList();
        if (modifierList != null) {
          final PsiAnnotation annotation = modifierList.findAnnotation(annotationClass.getName());
          if (annotation != null) {
            return annotation;
          }
        }
      }
    }
    return null;
  }

  private static void registerCollectionBinding(PsiType type,
                                                DomExtensionsRegistrar registrar,
                                                PsiAnnotation anno,
                                                PsiConstantEvaluationHelper evalHelper) {
    final boolean surroundWithTag = getBooleanAttribute(anno, "surroundWithTag", evalHelper);
    if (surroundWithTag) return; // todo Set, List, Array
    final String tagName = getStringAttribute(anno, "elementTag", evalHelper);
    final String attrName = getStringAttribute(anno, "elementValueAttribute", evalHelper);
    final PsiType elementType = getElementType(type);
    if (elementType == null || TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(elementType)
        || CommonClassNames.JAVA_LANG_STRING.equals(elementType.getCanonicalText())
        || TypeConversionUtil.isEnumType(elementType)) {
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
    }
    else {
      final PsiClass psiClass = PsiTypesUtil.getPsiClass(elementType);
      if (psiClass != null) {
        final PsiModifierList modifierList = psiClass.getModifierList();
        final PsiAnnotation tagAnno = modifierList == null? null : modifierList.findAnnotation(Tag.class.getName());
        final String classTagName = tagAnno == null? psiClass.getName() : getStringAttribute(tagAnno, "value", evalHelper);
        if (classTagName != null) {
          registrar.registerCollectionChildrenExtension(new XmlName(classTagName), DomElement.class).addExtender(new DomExtender() {
            @Override
            public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
              registerXmlb(registrar, psiClass, Collections.<With>emptyList());
            }
          });
        }
      }
    }
  }

  @Nullable
  static String getStringAttribute(final PsiAnnotation annotation,
                                   final String name,
                                   final PsiConstantEvaluationHelper evalHelper) {
    String value = getAttributeValue(annotation, name);
    if (value != null) return value;
    final Object o = evalHelper.computeConstantExpression(annotation.findAttributeValue(name), false);
    return o instanceof String && StringUtil.isNotEmpty((String)o)? (String)o : null;
  }

  private static boolean getBooleanAttribute(final PsiAnnotation annotation,
                                           final String name,
                                           final PsiConstantEvaluationHelper evalHelper) {
    String value = getAttributeValue(annotation, name);
    if (value != null) return Boolean.parseBoolean(value);
    final Object o = evalHelper.computeConstantExpression(annotation.findAttributeValue(name), false);
    return o instanceof Boolean && ((Boolean)o).booleanValue();
  }

  @Nullable
  private static String getAttributeValue(PsiAnnotation annotation, String name) {
    for (PsiNameValuePair attribute : annotation.getParameterList().getAttributes()) {
      if (name.equals(attribute.getName())) {
        return attribute.getLiteralValue();
      }
    }
    return null;
  }

  @Nullable
  public static PsiType getElementType(final PsiType psiType) {
    if (psiType instanceof PsiArrayType) {
      return ((PsiArrayType)psiType).getComponentType();
    }
    else if (psiType instanceof PsiClassType) {
      final PsiType[] types = ((PsiClassType)psiType).getParameters();
      return types.length == 1? types[0] : null;
    }
    else {
      return null;
    }
  }

  public static Collection<String> getDependencies(IdeaPlugin ideaPlugin) {
    Set<String> result = new HashSet<>();

    result.add(PluginManagerCore.CORE_PLUGIN_ID);

    for (Dependency dependency : ideaPlugin.getDependencies()) {
      ContainerUtil.addIfNotNull(result, dependency.getStringValue());
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
                ContainerUtil.addIfNotNull(result, mainPlugin.getPluginId());
                for (Dependency dependency : mainPlugin.getDependencies()) {
                  ContainerUtil.addIfNotNull(result, dependency.getStringValue());
                }
              }
            }
          }
        }
      }
    }

    return result;
  }

  public void registerExtensions(@NotNull final Extensions extensions, @NotNull final DomExtensionsRegistrar registrar) {
    final XmlElement xmlElement = extensions.getXmlElement();
    if (xmlElement == null) return;

    IdeaPlugin ideaPlugin = extensions.getParentOfType(IdeaPlugin.class, true);

    if (ideaPlugin == null) return;

    String epPrefix = extensions.getEpPrefix();
    for (IdeaPlugin plugin : getVisiblePlugins(ideaPlugin)) {
      final String pluginId = StringUtil.notNullize(plugin.getPluginId(), "com.intellij");
      for (ExtensionPoints points : plugin.getExtensionPoints()) {
        for (ExtensionPoint point : points.getExtensionPoints()) {
          registerExtensionPoint(registrar, point, epPrefix, pluginId);
        }
      }
    }
  }

  @Override
  public boolean supportsStubs() {
    return false;
  }

  interface SimpleTagValue extends DomElement {
    @SuppressWarnings("UnusedDeclaration")
    @TagValue
    String getTagValue();
  }

  @SuppressWarnings("ClassExplicitlyAnnotation")
  private static class MyRequired implements Required {
    @Override
    public boolean value() {
      return true;
    }

    @Override
    public boolean nonEmpty() {
      return true;
    }

    @Override
    public boolean identifier() {
      return false;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return Required.class;
    }
  }

  private static class MyExtendClass extends ExtendClassImpl {
    private final String myInterfaceName;

    private MyExtendClass(String interfaceName) {
      myInterfaceName = interfaceName;
    }

    @Override
    public boolean allowAbstract() {
      return false;
    }

    @Override
    public boolean allowInterface() {
      return false;
    }

    @Override
    public boolean allowEnum() {
      return false;
    }

    @Override
    public String value() {
      return myInterfaceName;
    }
  }

}
