// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.google.common.base.CaseFormat;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.With;
import org.jetbrains.idea.devkit.references.extensions.ExtensionPointBinding;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ExtensionDomExtender extends DomExtender<Extension> {

  private static final XmlName IMPLEMENTATION_XML_NAME = new XmlName(Extension.IMPLEMENTATION_ATTRIBUTE);

  private static final PsiClassConverter CLASS_CONVERTER = new PluginPsiClassConverter();
  private static final LanguageResolvingConverter LANGUAGE_CONVERTER = new LanguageResolvingConverter();
  private static final ActionOrGroupResolveConverter ACTION_CONVERTER = new ActionOrGroupResolveConverter.OnlyActions();

  @Override
  public void registerExtensions(@NotNull final Extension extension, @NotNull final DomExtensionsRegistrar registrar) {
    final ExtensionPoint extensionPoint = extension.getExtensionPoint();
    if (extensionPoint == null) return;

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

  private static void registerXmlb(DomExtensionsRegistrar registrar,
                                   @Nullable PsiClass psiClass,
                                   @NotNull List<With> elements) {
    if (psiClass == null) return;

    ExtensionPointBinding binding = new ExtensionPointBinding(psiClass);
    binding.visit(new ExtensionPointBinding.BindingVisitor() {

      @Override
      public void visitAttribute(@NotNull PsiField field, @NotNull @NonNls String attributeName, RequiredFlag required) {
        final With withElement = findWithElement(elements, field);
        final PsiType fieldType = field.getType();
        Class<?> clazz = String.class;
        if (PsiType.BOOLEAN.equals(fieldType)) {
          clazz = Boolean.class;
        }
        else if (PsiType.INT.equals(fieldType) ||
                 fieldType.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)) {
          clazz = Integer.class;
        }
        else if (withElement != null || Extension.isClassField(attributeName)) {
          clazz = PsiClass.class;
        }
        final DomExtension extension =
          registrar.registerGenericAttributeValueChildExtension(new XmlName(attributeName), clazz).setDeclaringElement(field);
        markAsRequired(extension, required);

        if (clazz == String.class) {
          markStringProperty(extension, field, fieldType, attributeName);
        }
        else if (clazz == PsiClass.class) {
          markAsClass(extension, true, withElement);
        }
      }

      @Override
      public void visitTagOrProperty(@NotNull PsiField field, @NotNull String tagName, RequiredFlag required) {
        final DomExtension extension =
          registrar.registerFixedNumberChildExtension(new XmlName(tagName), SimpleTagValue.class)
            .setDeclaringElement(field);
        markAsRequired(extension, required);

        final With withElement = findWithElement(elements, field);
        markAsClass(extension, Extension.isClassField(field.getName()), withElement);

        markStringProperty(extension, field, field.getType(), tagName);
      }

      @Override
      public void visitXCollection(@NotNull PsiField field,
                                   @Nullable String tagName,
                                   @NotNull PsiAnnotation collectionAnnotation,
                                   RequiredFlag required) {
        if (tagName == null) {
          registerCollectionBinding(field, registrar, collectionAnnotation, required);
          return;
        }

        registrar.registerFixedNumberChildExtension(new XmlName(tagName), DomElement.class).addExtender(new DomExtender() {
          @Override
          public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
            registerCollectionBinding(field, registrar, collectionAnnotation, required);
          }
        });
      }
    });
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

  private static void markAsClass(DomExtension extension, boolean isClassField, @Nullable With withElement) {
    if (withElement != null) {
      final String withClassName = withElement.getImplements().getStringValue();
      extension.addCustomAnnotation(new ExtendClassImpl() {
        @Override
        public String[] value() {
          return new String[]{withClassName};
        }
      });
    }
    if (withElement != null || isClassField) {
      extension.setConverter(CLASS_CONVERTER);
    }
  }

  private static void markAsRequired(DomExtension extension, ExtensionPointBinding.BindingVisitor.RequiredFlag required) {
    if (required == ExtensionPointBinding.BindingVisitor.RequiredFlag.REQUIRED) {
      extension.addCustomAnnotation(MyRequired.INSTANCE);
    }
    else if (required == ExtensionPointBinding.BindingVisitor.RequiredFlag.REQUIRED_ALLOW_EMPTY) {
      extension.addCustomAnnotation(MyRequiredCanBeEmpty.INSTANCE);
    }
  }

  private static void markStringProperty(DomExtension extension, PsiField field, PsiType fieldType, String propertyName) {
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

    if ("language".equals(propertyName) ||
        StringUtil.endsWith(propertyName, "Language")) // NON-NLS
    {
      extension.setConverter(LANGUAGE_CONVERTER);
    }
    else if ("action".equals(propertyName)) {
      extension.setConverter(ACTION_CONVERTER);
    }
  }

  private static void registerCollectionBinding(PsiField field,
                                                DomExtensionsRegistrar registrar,
                                                PsiAnnotation collectionAnnotation,
                                                ExtensionPointBinding.BindingVisitor.RequiredFlag required) {
    final boolean surroundWithTag = PsiUtil.getAnnotationBooleanAttribute(collectionAnnotation, "surroundWithTag");
    if (surroundWithTag) return; // todo Set, List, Array

    final String tagName = PsiUtil.getAnnotationStringAttribute(collectionAnnotation, "elementTag", null);
    final String attrName = PsiUtil.getAnnotationStringAttribute(collectionAnnotation, "elementValueAttribute", null);
    final PsiType elementType = getElementType(field.getType());
    if (elementType == null || TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(elementType)
        || CommonClassNames.JAVA_LANG_STRING.equals(elementType.getCanonicalText())
        || TypeConversionUtil.isEnumType(elementType)) {
      if (tagName != null && attrName == null) {
        final DomExtension extension = registrar
          .registerCollectionChildrenExtension(new XmlName(tagName), SimpleTagValue.class)
          .setDeclaringElement(field);
        markAsRequired(extension, required);
      }
      else if (tagName != null) {
        final DomExtension extension = registrar
          .registerCollectionChildrenExtension(new XmlName(tagName), DomElement.class)
          .setDeclaringElement(field);
        markAsRequired(extension, required);
        extension.addExtender(new DomExtender() {
          @Override
          public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
            registrar.registerGenericAttributeValueChildExtension(new XmlName(attrName), String.class);
          }
        });
      }
    }
    else {
      final PsiClass elementPsiClass = PsiTypesUtil.getPsiClass(elementType);
      if (elementPsiClass != null) {
        final PsiModifierList modifierList = elementPsiClass.getModifierList();
        final PsiAnnotation tagAnno = modifierList == null ? null : modifierList.findAnnotation(Tag.class.getName());
        final String classTagName = tagAnno == null ? elementPsiClass.getName() :
                                    PsiUtil.getAnnotationStringAttribute(tagAnno, "value", null);
        if (classTagName != null) {
          final DomExtension extension = registrar
            .registerCollectionChildrenExtension(new XmlName(classTagName), DomElement.class)
            .setDeclaringElement(field);
          markAsRequired(extension, required);
          extension.addExtender(new DomExtender() {
            @Override
            public void registerExtensions(@NotNull DomElement domElement, @NotNull DomExtensionsRegistrar registrar) {
              registerXmlb(registrar, elementPsiClass, Collections.emptyList());
            }
          });
        }
      }
    }
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

  @SuppressWarnings("ClassExplicitlyAnnotation")
  private static class MyRequiredCanBeEmpty implements Required {

    private static final MyRequiredCanBeEmpty INSTANCE = new MyRequiredCanBeEmpty();

    @Override
    public boolean value() {
      return true;
    }

    @Override
    public boolean nonEmpty() {
      return false;
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

  private static final class MyImplementationExtendClass extends ExtendClassImpl {
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
    public String[] value() {
      return new String[]{myInterfaceName};
    }
  }

  @NotNull
  private static ResolvingConverter<PsiEnumConstant> createEnumConverter(PsiClass fieldPsiClass) {
    return new PsiEnumConstantResolvingConverter(fieldPsiClass.getQualifiedName());
  }

  private static class PsiEnumConstantResolvingConverter extends ResolvingConverter<PsiEnumConstant> {

    private final String myEnumFqn;

    private static final Set<String> LEGACY_ENUM_NOTATION_CLASSES =
      ContainerUtil.immutableSet(
        "com.intellij.compiler.CompileTaskBean.CompileTaskExecutionPhase",
        "com.intellij.plugins.jboss.arquillian.configuration.container.ArquillianContainerKind",
        "com.intellij.notification.impl.NotificationGroupEP.DisplayType"
      );

    private static final Set<String> LOWER_UNDERSCORE_ENUM_NOTATION_CLASSES =
      ContainerUtil.immutableSet(
        "com.intellij.ui.viewModel.extraction.ToolWindowExtractorMode"
      );

    PsiEnumConstantResolvingConverter(String enumFqn) { myEnumFqn = enumFqn; }

    @Override
    public String getErrorMessage(@Nullable String s, ConvertContext context) {
      return DevKitBundle.message("plugin.xml.convert.enum.cannot.resolve", s, myEnumFqn);
    }

    @NotNull
    @Override
    public Collection<? extends PsiEnumConstant> getVariants(ConvertContext context) {
      PsiClass enumClass = getEnumClass(context);
      if (enumClass == null) return Collections.emptyList();

      return ContainerUtil.findAll(enumClass.getFields(), PsiEnumConstant.class);
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

      PsiClass enumClass = getEnumClass(context);
      if (enumClass == null) return null;

      final PsiField name = enumClass.findFieldByName(fromXmlName(s), false);
      return name instanceof PsiEnumConstant ? (PsiEnumConstant)name : null;
    }

    @Nullable
    @Override
    public String toString(@Nullable PsiEnumConstant constant, ConvertContext context) {
      return constant == null ? null : toXmlName(constant);
    }

    @Nullable
    private PsiClass getEnumClass(ConvertContext context) {
      return DomJavaUtil.findClass(myEnumFqn, context.getInvocationElement());
    }

    private String fromXmlName(@NotNull String name) {
      if (doNotTransformName()) return name;
      if (enumLowerUnderscore()) return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, name);
      return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, name);
    }

    private String toXmlName(PsiEnumConstant constant) {
      String name = constant.getName();
      if (doNotTransformName()) return name;
      if (enumLowerUnderscore()) return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_UNDERSCORE, name);
      return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name);
    }

    private boolean doNotTransformName() {
      return LEGACY_ENUM_NOTATION_CLASSES.contains(myEnumFqn);
    }

    private boolean enumLowerUnderscore() {
      return LOWER_UNDERSCORE_ENUM_NOTATION_CLASSES.contains(myEnumFqn);
    }
  }
}
