// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.google.common.base.CaseFormat;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
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
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
      if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
      registerField(registrar, field, findWithElement(elements, field));
    }
  }

  @Nullable
  static With findWithElement(List<? extends With> withElements, PsiField field) {
    for (With with : withElements) {
      PsiField withPsiField = DomUtil.hasXml(with.getTag()) ? with.getTag().getValue() : with.getAttribute().getValue();
      if (field.getManager().areElementsEquivalent(field, withPsiField)) {
        return with;
      }
    }
    return null;
  }

  private static void registerField(final DomExtensionsRegistrar registrar, @NotNull final PsiField field, @Nullable With withElement) {
    final PsiMethod getter = PropertyUtilBase.findGetterForField(field);
    final PsiMethod setter = PropertyUtilBase.findSetterForField(field);
    if (!field.hasModifierProperty(PsiModifier.PUBLIC) && (getter == null || setter == null)) {
      return;
    }

    String fieldName = field.getName();
    final PsiAnnotation attributeAnnotation = PsiUtil.findAnnotation(Attribute.class, field, getter, setter);
    final PsiType fieldType = field.getType();
    if (attributeAnnotation != null) {
      fieldName = getStringAttribute(attributeAnnotation, "value", fieldName);
      if (fieldName != null) {
        Class clazz = String.class;
        if (withElement != null || Extension.isClassField(fieldName)) {
          clazz = PsiClass.class;
        }
        else if (PsiType.BOOLEAN.equals(fieldType)) {
          clazz = Boolean.class;
        }
        else if (PsiType.INT.equals(fieldType) ||
                 fieldType.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)) {
          clazz = Integer.class;
        }
        final DomExtension extension =
          registrar.registerGenericAttributeValueChildExtension(new XmlName(fieldName), clazz).setDeclaringElement(field);
        if (PsiUtil.findAnnotation(RequiredElement.class, field) != null) {
          extension.addCustomAnnotation(MyRequired.INSTANCE);
        }

        if (clazz == String.class) {
          if (PsiUtil.findAnnotation(NonNls.class, field) != null) {
            extension.addCustomAnnotation(MyNoSpellchecking.INSTANCE);
          }
          else if (!fieldType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            final PsiClass fieldPsiClass = PsiTypesUtil.getPsiClass(fieldType);
            if (fieldPsiClass != null && fieldPsiClass.isEnum()) {
              extension.setConverter(createEnumConverter(fieldPsiClass));
              return;
            }
          }

          markAsLanguage(extension, fieldName);
        }
        else if (clazz == PsiClass.class) {
          markAsClass(extension, true, withElement);
        }
      }
      return;
    }

    final PsiAnnotation tagAnno = PsiUtil.findAnnotation(Tag.class, field, getter, setter);
    final PsiAnnotation propAnno = PsiUtil.findAnnotation(Property.class, field, getter, setter);
    final PsiAnnotation collectionAnnotation = PsiUtil.findAnnotation(XCollection.class, field, getter, setter);
    //final PsiAnnotation colAnno = modifierList.findAnnotation(Collection.class.getName()); // todo
    final String tagName = tagAnno != null ? getStringAttribute(tagAnno, "value", fieldName) :
                           propAnno != null && getBooleanAttribute(propAnno, "surroundWithTag") ? Constants.OPTION : null;
    if (tagName != null) {
      if (collectionAnnotation == null) {
        final DomExtension extension =
          registrar.registerFixedNumberChildExtension(new XmlName(tagName), SimpleTagValue.class)
            .setDeclaringElement(field);
        markAsClass(extension, Extension.isClassField(fieldName), withElement);
        if (PsiUtil.findAnnotation(NonNls.class, field) != null) {
          extension.addCustomAnnotation(MyNoSpellchecking.INSTANCE);
        }
      }
      else {
        registrar.registerFixedNumberChildExtension(new XmlName(tagName), DomElement.class).addExtender(new DomExtender() {
          @Override
          public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
            registerCollectionBinding(fieldType, registrar, collectionAnnotation);
          }
        });
      }
    }
    else if (collectionAnnotation != null) {
      registerCollectionBinding(fieldType, registrar, collectionAnnotation);
    }
  }

  private static void markAsLanguage(DomExtension extension, String fieldName) {
    if ("language".equals(fieldName)) {
      extension.setConverter(LANGUAGE_CONVERTER);
    }
  }

  private static void markAsClass(DomExtension extension, boolean isClassField, @Nullable With withElement) {
    if (withElement != null) {
      final String withClassName = withElement.getImplements().getStringValue();
      extension.addCustomAnnotation(new ExtendClassImpl() {
        @Override
        public String value() {
          return withClassName;
        }
      });
    }
    if (withElement != null || isClassField) {
      extension.setConverter(CLASS_CONVERTER);
    }
  }

  private static void registerCollectionBinding(PsiType type,
                                                DomExtensionsRegistrar registrar,
                                                PsiAnnotation anno) {
    final boolean surroundWithTag = getBooleanAttribute(anno, "surroundWithTag");
    if (surroundWithTag) return; // todo Set, List, Array

    final String tagName = getStringAttribute(anno, "elementTag", null);
    final String attrName = getStringAttribute(anno, "elementValueAttribute", null);
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
        final String classTagName = tagAnno == null ? psiClass.getName() : getStringAttribute(tagAnno, "value", null);
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
  private static String getStringAttribute(final PsiAnnotation annotation,
                                           final String name,
                                           String defaultValueIfEmpty) {
    final String value = AnnotationUtil.getDeclaredStringAttributeValue(annotation, name);
    return StringUtil.defaultIfEmpty(value, defaultValueIfEmpty);
  }

  private static boolean getBooleanAttribute(final PsiAnnotation annotation,
                                             final String name) {
    return ObjectUtils.notNull(AnnotationUtil.getBooleanAttributeValue(annotation, name), Boolean.FALSE);
  }

  @Nullable
  private static PsiType getElementType(final PsiType psiType) {
    if (psiType instanceof PsiArrayType) {
      return ((PsiArrayType)psiType).getComponentType();
    }
    if (psiType instanceof PsiClassType) {
      final PsiType[] types = ((PsiClassType)psiType).getParameters();
      return types.length == 1 ? types[0] : null;
    }
    return null;
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

  @NotNull
  private static ResolvingConverter<PsiEnumConstant> createEnumConverter(PsiClass fieldPsiClass) {
    return new ResolvingConverter<PsiEnumConstant>() {

      @Override
      public String getErrorMessage(@Nullable String s, ConvertContext context) {
        return "Cannot resolve '" + s + "' in " + fieldPsiClass.getQualifiedName();
      }

      @NotNull
      @Override
      public Collection<? extends PsiEnumConstant> getVariants(ConvertContext context) {
        return ContainerUtil.findAll(fieldPsiClass.getFields(), PsiEnumConstant.class);
      }

      @Nullable
      @Override
      public LookupElement createLookupElement(PsiEnumConstant constant) {
        return JavaLookupElementBuilder.forField(constant, toXmlName(constant), null);
      }

      @Nullable
      @Override
      public PsiEnumConstant fromString(@Nullable String s, ConvertContext context) {
        if (s == null) return null;

        final PsiField name = fieldPsiClass.findFieldByName(fromXmlName(s), false);
        return name instanceof PsiEnumConstant ? (PsiEnumConstant)name : null;
      }

      @Nullable
      @Override
      public String toString(@Nullable PsiEnumConstant constant, ConvertContext context) {
        return constant == null ? null : toXmlName(constant);
      }

      private String fromXmlName(@NotNull String name) {
        if (doNotTransformName()) return name;
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, name);
      }

      private String toXmlName(PsiEnumConstant constant) {
        if (doNotTransformName()) return constant.getName();
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, constant.getName());
      }

      private boolean doNotTransformName() {
        return LEGACY_ENUM_NOTATION_CLASSES.contains(fieldPsiClass.getQualifiedName());
      }
    };
  }

  private static final Set<String> LEGACY_ENUM_NOTATION_CLASSES =
    ContainerUtil.immutableSet(
      "com.intellij.compiler.CompileTaskBean.CompileTaskExecutionPhase",
      "com.intellij.plugins.jboss.arquillian.configuration.container.ArquillianContainerKind"
    );
}
