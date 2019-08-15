// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import com.intellij.util.xmlb.Constants;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.With;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public class ExtensionDomExtender extends DomExtender<Extension> {

  private static final XmlName IMPLEMENTATION_XML_NAME = new XmlName(Extension.IMPLEMENTATION_ATTRIBUTE);

  private static final PsiClassConverter CLASS_CONVERTER = new PluginPsiClassConverter();
  private static final LanguageResolvingConverter LANGUAGE_CONVERTER = new LanguageResolvingConverter();

  @Override
  public void registerExtensions(@NotNull final Extension extension, @NotNull final DomExtensionsRegistrar registrar) {
    final ExtensionPoint extensionPoint = extension.getExtensionPoint();
    assert extensionPoint != null;

    final String interfaceName = extensionPoint.getInterface().getStringValue();
    if (interfaceName != null) {
      final DomExtension implementationAttribute =
        registrar.registerGenericAttributeValueChildExtension(IMPLEMENTATION_XML_NAME, PsiClass.class)
          .setConverter(CLASS_CONVERTER)
          .addCustomAnnotation(new MyImplementationExtendClass(interfaceName))
          .addCustomAnnotation(MyRequired.INSTANCE);

      final PsiClass interfaceClass = extensionPoint.getInterface().getValue();
      if (interfaceClass != null) {
        implementationAttribute.setDeclaringElement(interfaceClass);
      }
      else {
        implementationAttribute.setDeclaringElement(extensionPoint);
      }

      registerXmlb(registrar, interfaceClass, Collections.emptyList());
    }
    else {
      final PsiClass beanClass = extensionPoint.getBeanClass().getValue();
      registerXmlb(registrar, beanClass, extensionPoint.getWithElements());
    }
  }

  private static void registerXmlb(final DomExtensionsRegistrar registrar,
                                   @Nullable final PsiClass beanClass,
                                   @NotNull List<With> elements) {
    if (beanClass == null) return;

    PsiField[] fields;
    UClass beanClassNavigationClass = UastContextKt.toUElement(beanClass.getNavigationElement(), UClass.class);
    if (beanClassNavigationClass != null) {
      fields = beanClassNavigationClass.getAllFields();
    }
    else {
      fields = beanClass.getAllFields(); // fallback
    }

    for (PsiField field : fields) {
      registerField(registrar, field, findWithElement(elements, field));
    }
  }

  @Nullable
  public static With findWithElement(List<? extends With> elements, PsiField field) {
    for (With element : elements) {
      if (Comparing.equal(field.getName(), element.getAttribute().getStringValue())) {
        return element;
      }
    }
    return null;
  }

  private static void registerField(final DomExtensionsRegistrar registrar, @NotNull final PsiField field, With withElement) {
    final PsiMethod getter = PropertyUtilBase.findGetterForField(field);
    final PsiMethod setter = PropertyUtilBase.findSetterForField(field);
    if (!field.hasModifierProperty(PsiModifier.PUBLIC) && (getter == null || setter == null)) {
      return;
    }

    final String fieldName = field.getName();
    assert fieldName != null;
    final PsiConstantEvaluationHelper evalHelper = JavaPsiFacade.getInstance(field.getProject()).getConstantEvaluationHelper();
    final PsiAnnotation attrAnno = findAnnotation(Attribute.class, field, getter, setter);
    if (attrAnno != null) {
      final String attrName = getStringAttribute(attrAnno, "value", evalHelper, fieldName);
      if (attrName != null) {
        Class clazz = String.class;
        if (withElement != null || Extension.isClassField(fieldName)) {
          clazz = PsiClass.class;
        }
        else if (PsiType.BOOLEAN.equals(field.getType())) {
          clazz = Boolean.class;
        }
        final DomExtension extension =
          registrar.registerGenericAttributeValueChildExtension(new XmlName(attrName), clazz).setDeclaringElement(field);
        if (findAnnotation(RequiredElement.class, field) != null) {
          extension.addCustomAnnotation(MyRequired.INSTANCE);
        }
        if (clazz == String.class && findAnnotation(NonNls.class, field) != null) {
          extension.addCustomAnnotation(MyNoSpellchecking.INSTANCE);
        }

        markAsClass(extension, fieldName, withElement);
        if (clazz.equals(String.class)) {
          markAsLanguage(extension, fieldName);
        }
      }
      return;
    }

    final PsiAnnotation tagAnno = findAnnotation(Tag.class, field, getter, setter);
    final PsiAnnotation propAnno = findAnnotation(Property.class, field, getter, setter);
    final PsiAnnotation collectionAnnotation = findAnnotation(XCollection.class, field, getter, setter);
    //final PsiAnnotation colAnno = modifierList.findAnnotation(Collection.class.getName()); // todo
    final String tagName = tagAnno != null ? getStringAttribute(tagAnno, "value", evalHelper, fieldName) :
                           propAnno != null && getBooleanAttribute(propAnno, "surroundWithTag", evalHelper) ? Constants.OPTION : null;
    if (tagName != null) {
      if (collectionAnnotation == null) {
        final DomExtension extension =
          registrar.registerFixedNumberChildExtension(new XmlName(tagName), SimpleTagValue.class)
            .setDeclaringElement(field);
        markAsClass(extension, fieldName, withElement);
        if (findAnnotation(NonNls.class, field) != null) {
          extension.addCustomAnnotation(MyNoSpellchecking.INSTANCE);
        }
      }
      else {
        registrar.registerFixedNumberChildExtension(new XmlName(tagName), DomElement.class).addExtender(new DomExtender() {
          @Override
          public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
            registerCollectionBinding(field.getType(), registrar, collectionAnnotation, evalHelper);
          }
        });
      }
    }
    else if (collectionAnnotation != null) {
      registerCollectionBinding(field.getType(), registrar, collectionAnnotation, evalHelper);
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
    if (withElement != null || Extension.isClassField(fieldName)) {
      extension.setConverter(CLASS_CONVERTER);
    }
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
    final String tagName = getStringAttribute(anno, "elementTag", evalHelper, null);
    final String attrName = getStringAttribute(anno, "elementValueAttribute", evalHelper, null);
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
        final PsiAnnotation tagAnno = modifierList == null ? null : modifierList.findAnnotation(Tag.class.getName());
        final String classTagName = tagAnno == null ? psiClass.getName() : getStringAttribute(tagAnno, "value", evalHelper, null);
        if (classTagName != null) {
          registrar.registerCollectionChildrenExtension(new XmlName(classTagName), DomElement.class).addExtender(new DomExtender() {
            @Override
            public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
              registerXmlb(registrar, psiClass, Collections.emptyList());
            }
          });
        }
      }
    }
  }

  @Nullable
  static String getStringAttribute(final PsiAnnotation annotation,
                                   final String name,
                                   final PsiConstantEvaluationHelper evalHelper, String defaultValueIfEmpty) {
    String value = getAttributeValue(annotation, name);
    if (value != null) {
      return value.isEmpty() ? defaultValueIfEmpty : value;
    }
    Object o = evalHelper.computeConstantExpression(annotation.findAttributeValue(name), false);
    if (!(o instanceof String)) return null;
    return StringUtil.isNotEmpty((String)o) ? (String)o : defaultValueIfEmpty;
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
      return types.length == 1 ? types[0] : null;
    }
    else {
      return null;
    }
  }

  public interface SimpleTagValue extends GenericDomValue<String> {
  }

  @SuppressWarnings("ClassExplicitlyAnnotation")
  private static class MyNoSpellchecking implements NoSpellchecking {

    private static final MyNoSpellchecking INSTANCE = new MyNoSpellchecking();

    @Override
    public Class<? extends Annotation> annotationType() {
      return NoSpellchecking.class;
    }
  }

  @SuppressWarnings("ClassExplicitlyAnnotation")
  private static class MyRequired implements Required {

    private static final MyRequired INSTANCE = new MyRequired();

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

  private static class MyImplementationExtendClass extends ExtendClassImpl {
    private final String myInterfaceName;

    private MyImplementationExtendClass(String interfaceName) {
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
